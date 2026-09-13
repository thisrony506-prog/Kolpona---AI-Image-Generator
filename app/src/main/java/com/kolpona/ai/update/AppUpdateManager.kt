package com.kolpona.ai.update

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kolpona.ai.BuildConfig
import com.kolpona.ai.data.prefs.AppPreferences
import com.kolpona.ai.data.prefs.PendingUpdate
import com.kolpona.ai.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Current : UpdateUiState
    data class Required(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val phase: UpdatePhase,
        val progress: Int = 0,
        val error: String? = null
    ) : UpdateUiState
}

enum class UpdatePhase {
    ReadyToDownload,
    Downloading,
    ReadyToInstall,
    NeedsPermission,
    Installing
}

class AppUpdateManager(
    private val app: Application,
    private val preferences: AppPreferences,
    private val networkMonitor: NetworkMonitor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Kolpona/${BuildConfig.VERSION_NAME} (Android)")
                    .header("Accept", "*/*")
                    .build()
            )
        }
        .build()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check() {
        scope.launch { performCheck() }
    }

    fun onForeground() {
        scope.launch {
            val current = _state.value
            if (current is UpdateUiState.Required &&
                current.phase == UpdatePhase.NeedsPermission &&
                canRequestInstalls()
            ) {
                installInternal()
                return@launch
            }
            if (current is UpdateUiState.Required && current.phase == UpdatePhase.Downloading) {
                return@launch
            }
            performCheck()
        }
    }

    fun download() {
        scope.launch { performDownload() }
    }

    fun install(activity: Activity) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canRequestInstalls()) {
                emitRequired(phase = UpdatePhase.NeedsPermission)
                withContext(Dispatchers.Main) {
                    openInstallPermissionSettings(activity)
                }
                return@launch
            }
            installInternal(activity)
        }
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    fun retry() {
        scope.launch {
            val pending = preferences.getPendingUpdate()
            val apk = pending?.let { apkFile(it.versionCode) }
            if (pending != null && apk != null && apk.isValidApk()) {
                emitRequired(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    notes = pending.notes,
                    phase = if (canRequestInstalls()) UpdatePhase.ReadyToInstall else UpdatePhase.NeedsPermission
                )
                return@launch
            }
            if (pending != null) {
                performDownload()
            } else {
                performCheck(forceNetwork = true)
            }
        }
    }

    private suspend fun performCheck(forceNetwork: Boolean = false) {
        mutex.withLock {
            val pending = preferences.getPendingUpdate()
            if (pending != null && pending.versionCode > BuildConfig.VERSION_CODE) {
                val apk = apkFile(pending.versionCode)
                val phase = when {
                    apk != null && apk.isValidApk() && canRequestInstalls() -> UpdatePhase.ReadyToInstall
                    apk != null && apk.isValidApk() -> UpdatePhase.NeedsPermission
                    else -> UpdatePhase.ReadyToDownload
                }
                emitRequired(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    notes = pending.notes,
                    phase = phase
                )
            } else if (_state.value !is UpdateUiState.Required) {
                _state.value = UpdateUiState.Checking
            }

            if (!forceNetwork && !networkMonitor.isOnline() && pending != null &&
                pending.versionCode > BuildConfig.VERSION_CODE
            ) {
                return
            }

            val remote = fetchManifest()
            if (remote == null) {
                if (pending == null || pending.versionCode <= BuildConfig.VERSION_CODE) {
                    preferences.clearPendingUpdate()
                    _state.value = UpdateUiState.Current
                }
                return
            }

            if (remote.versionCode <= BuildConfig.VERSION_CODE) {
                preferences.clearPendingUpdate()
                apkDir().listFiles()?.forEach { it.delete() }
                _state.value = UpdateUiState.Current
                return
            }

            preferences.setPendingUpdate(remote)
            val apk = apkFile(remote.versionCode)
            val phase = when {
                apk != null && apk.isValidApk() && canRequestInstalls() -> UpdatePhase.ReadyToInstall
                apk != null && apk.isValidApk() -> UpdatePhase.NeedsPermission
                else -> UpdatePhase.ReadyToDownload
            }
            emitRequired(
                versionName = remote.versionName,
                versionCode = remote.versionCode,
                notes = remote.notes,
                phase = phase
            )
        }
    }

    private suspend fun performDownload() {
        val pending = preferences.getPendingUpdate() ?: run {
            performCheck(forceNetwork = true)
            preferences.getPendingUpdate()
        } ?: return

        mutex.withLock {
            emitRequired(
                versionName = pending.versionName,
                versionCode = pending.versionCode,
                notes = pending.notes,
                phase = UpdatePhase.Downloading,
                progress = 0
            )
            try {
                val dir = apkDir().apply { mkdirs() }
                val dest = File(dir, "kolpona-${pending.versionCode}.apk")
                val temp = File(dir, "kolpona-${pending.versionCode}.apk.part")
                temp.delete()
                dest.delete()

                val request = Request.Builder().url(pending.apkUrl).get().build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("http ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("empty")
                    val contentType = body.contentType()?.toString().orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        throw IllegalStateException("html")
                    }
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buf = ByteArray(32 * 1024)
                            var read = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                read += n
                                val pct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 99) else -1
                                if (pct != lastPct) {
                                    lastPct = pct
                                    emitRequired(
                                        versionName = pending.versionName,
                                        versionCode = pending.versionCode,
                                        notes = pending.notes,
                                        phase = UpdatePhase.Downloading,
                                        progress = pct.coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                    }
                }

                if (temp.length() < UpdateConfig.MIN_APK_BYTES) {
                    temp.delete()
                    throw IllegalStateException("too-small")
                }
                if (pending.sha256.isNotBlank()) {
                    val actual = sha256(temp)
                    if (!actual.equals(pending.sha256, ignoreCase = true)) {
                        temp.delete()
                        throw IllegalStateException("checksum")
                    }
                }
                if (!temp.renameTo(dest)) {
                    temp.copyTo(dest, overwrite = true)
                    temp.delete()
                }
                verifyArchive(dest)

                val phase = if (canRequestInstalls()) {
                    UpdatePhase.ReadyToInstall
                } else {
                    UpdatePhase.NeedsPermission
                }
                emitRequired(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    notes = pending.notes,
                    phase = phase,
                    progress = 100
                )
            } catch (_: Exception) {
                emitRequired(
                    versionName = pending.versionName,
                    versionCode = pending.versionCode,
                    notes = pending.notes,
                    phase = UpdatePhase.ReadyToDownload,
                    error = "download"
                )
            }
        }
    }

    private suspend fun installInternal(activity: Activity? = null) {
        val pending = preferences.getPendingUpdate()
        val file = pending?.let { apkFile(it.versionCode) }
        if (pending == null || file == null || !file.isValidApk()) {
            scope.launch { performDownload() }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canRequestInstalls()) {
            emitRequired(
                versionName = pending.versionName,
                versionCode = pending.versionCode,
                notes = pending.notes,
                phase = UpdatePhase.NeedsPermission
            )
            return
        }
        emitRequired(
            versionName = pending.versionName,
            versionCode = pending.versionCode,
            notes = pending.notes,
            phase = UpdatePhase.Installing,
            progress = 100
        )
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val intentHost = activity ?: app
        withContext(Dispatchers.Main) {
            intentHost.startActivity(intent)
        }
    }

    private suspend fun fetchManifest(): PendingUpdate? = withContext(Dispatchers.IO) {
        val urls = listOf(UpdateConfig.MANIFEST_URL, UpdateConfig.FALLBACK_MANIFEST_URL)
        for (url in urls) {
            val parsed = runCatching { readManifest(url) }.getOrNull()
            if (parsed != null) return@withContext parsed
        }
        null
    }

    private fun readManifest(url: String): PendingUpdate? {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string().orEmpty()
            if (text.isBlank() || text.trimStart().startsWith("<")) return null
            val json = JSONObject(text)
            val code = json.optInt("versionCode", 0)
            if (code <= 0) return null
            val name = json.optString("versionName").ifBlank { code.toString() }
            val apkUrl = json.optString("apkUrl").ifBlank { UpdateConfig.DEFAULT_APK_URL }
            val sha = json.optString("sha256")
            val notes = json.optString("notes")
            return PendingUpdate(
                versionCode = code,
                versionName = name,
                apkUrl = apkUrl,
                sha256 = sha,
                notes = notes
            )
        }
    }

    private fun emitRequired(
        versionName: String? = null,
        versionCode: Int? = null,
        notes: String? = null,
        phase: UpdatePhase,
        progress: Int = 0,
        error: String? = null
    ) {
        val current = _state.value as? UpdateUiState.Required
        _state.value = UpdateUiState.Required(
            versionName = versionName ?: current?.versionName.orEmpty(),
            versionCode = versionCode ?: current?.versionCode ?: 0,
            notes = notes ?: current?.notes.orEmpty(),
            phase = phase,
            progress = progress,
            error = error
        )
    }

    private fun apkDir(): File = File(app.cacheDir, "updates")

    private fun apkFile(versionCode: Int): File? {
        val file = File(apkDir(), "kolpona-$versionCode.apk")
        return file.takeIf { it.exists() }
    }

    private fun File.isValidApk(): Boolean = exists() && length() >= UpdateConfig.MIN_APK_BYTES

    private fun canRequestInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun verifyArchive(file: File) {
        val info = app.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IllegalStateException("not-apk")
        if (info.packageName != app.packageName) {
            file.delete()
            throw IllegalStateException("package")
        }
        @Suppress("DEPRECATION")
        val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            info.versionCode
        }
        if (downloadedCode <= BuildConfig.VERSION_CODE) {
            file.delete()
            throw IllegalStateException("stale")
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
