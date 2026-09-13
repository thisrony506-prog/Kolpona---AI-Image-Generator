# Kolpona – AI Image Generator

Turn Your Imagination Into Images.

Native Android app (Kotlin, Jetpack Compose, Material 3) that generates images with Pollinations AI, manages 100 daily credits locally, and offers optional Start.io rewarded videos for +25 credits.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 24 / targetSdk 35

## Open and run

1. Clone this repository.
2. Copy `local.properties.example` to `local.properties` (Android Studio also writes `sdk.dir` automatically).
3. Add your Pollinations key (optional for some public models, required for authenticated `gen.pollinations.ai` usage):

```
POLLINATIONS_API_KEY=your_key_here
```

4. Open the project in Android Studio and run the **debug** build.

Debug builds enable Start.io test ads (`IS_AD_TEST_MODE = true`). Release builds set that flag to `false`.

## Daily credits

| Event | Amount |
| --- | --- |
| Daily reset | 100 credits |
| Successful image generation | −25 credits |
| Completed rewarded video | +25 credits |

Credits reset once per local calendar day to exactly 100. Extra credits from ads do not carry past midnight. Credits are **not** deducted if generation fails.

## Project layout

```
app/src/main/java/com/kolpona/app/
  ads/          Start.io rewarded video
  data/api/     Pollinations client (isolated)
  data/database Room history
  data/prefs    DataStore (credits, settings)
  data/repository
  domain/manager CreditManager + CreditConfig
  ui/           Compose screens
```

## Configuration cheat sheet

### 1. Pollinations configuration

- **Code:** `app/src/main/java/com/kolpona/app/data/api/PollinationsConfig.kt`
  - `API_BASE_URL` = `https://gen.pollinations.ai`
  - `DEFAULT_MODEL` = `flux`
  - `apiKey` is read from `BuildConfig.POLLINATIONS_API_KEY` (never logged or shown)
- **Service:** `app/src/main/java/com/kolpona/app/data/api/PollinationsApiService.kt`

### 2. Start.io App ID

- **Code:** `app/src/main/java/com/kolpona/app/ads/AdsConfig.kt` (`APP_ID = "208407601"`)
- **Manifest meta-data:** `app/src/main/AndroidManifest.xml` (`com.startapp.sdk.APPLICATION_ID`)

### 3. Replace the Pollinations API credential

Do **not** put the key in Kotlin source, UI, logs, Room, or Git.

1. Set `POLLINATIONS_API_KEY` in **`local.properties`** (gitignored), or
2. Set the GitHub Actions secret **`POLLINATIONS_API_KEY`**, or
3. Export the same environment variable when assembling.

`app/build.gradle.kts` copies that value into `BuildConfig` at compile time.

Before production, point the app at a backend proxy by replacing `PollinationsApiService` only. Screens do not touch the secret.

### 4. Change daily credits

Edit `DAILY_INITIAL_CREDITS` in:

`app/src/main/java/com/kolpona/app/domain/manager/CreditConfig.kt`

### 5. Change image-generation cost

Edit `GENERATION_COST` in the same `CreditConfig.kt` file.

### 6. Change rewarded-video reward

Edit `REWARDED_VIDEO_REWARD` in the same `CreditConfig.kt` file.

### 7. Mandatory in-app updates

Each CI build publishes a public `version.json` + APK on the GitHub Release tag `kolpona-release-apk`. The app checks that file on launch. If `versionCode` is higher, a blocking Update screen appears: no Skip / Later / Close. The APK downloads inside the app, then Android’s installer runs. Chat / History / Settings stay locked until the new version is installed.

**Do not add a GitHub token (or any update secret) to the Android app.** The repository is public; a token inside the APK would leak. `GITHUB_TOKEN` is provided automatically to Actions for creating the release.

Existing CI secrets (generation only, not updates):

- `POLLINATIONS_API_KEY`
- `HUGGINGFACE_API_KEY`

### 8. Build the Release APK with GitHub Actions

Workflow: `.github/workflows/android.yml`

It checks out the repo, sets up JDK 17 and the Android SDK, writes `local.properties`, runs `./gradlew assembleRelease`, and uploads:

`app/build/outputs/apk/release/app-release.apk`

Add repository secret `POLLINATIONS_API_KEY` if you want the key baked into CI artifacts. Release signing uses a production keystore when `RELEASE_STORE_FILE` (and related passwords) are provided; otherwise the debug keystore is used so CI still produces `app-release.apk`.

Locally:

```
./gradlew assembleRelease
```

## Start.io test mode

| Build | `BuildConfig.IS_AD_TEST_MODE` |
| --- | --- |
| debug | `true` |
| release | `false` |

Never ship a production build with test ads enabled.

## Security

- Secrets live in `local.properties` / CI secrets, not in Git.
- Authorization is an OkHttp interceptor header. The key is never added to image URLs, Logcat, errors, or the database.
- HTTPS only (`network_security_config`).

## License

Use and modify for your product. Replace the temporary Pollinations development credential before a public release.
