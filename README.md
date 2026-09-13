# Kolpona – AI Image Generator

Turn Your Imagination Into Images.

Native Android app (Kotlin, Jetpack Compose, Material 3) that races Hugging Face FLUX.1-dev and Cloudflare FLUX.1-schnell, keeps 100 daily credits on-device, and offers optional Start.io rewarded videos for +25 credits.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35
- minSdk 24 / targetSdk 35

## Open and run

1. Clone this repository.
2. Copy `local.properties.example` to `local.properties` (Android Studio also writes `sdk.dir` automatically).
3. Add generation credentials (gitignored):

```
HUGGINGFACE_API_KEY=hf_...
CLOUDFLARE_ACCOUNT_ID=...
CLOUDFLARE_API_TOKEN=...
```

4. Open the project in Android Studio and run the **debug** build.

Debug builds enable Start.io test ads (`IS_AD_TEST_MODE = true`). Release builds set that flag to `false`.

## Generation architecture

```
User prompt
   |
   +--> Hugging Face FLUX.1-dev
   +--> Cloudflare FLUX.1-schnell
   |
   First valid image wins
```

Video uses Hugging Face (`THUDM/CogVideoX-5b`, then LTX / Wan fallback). Fake media is never returned.

Do not put keys in Kotlin source, UI, logs, Room, or Git.

## Daily credits

| Event | Amount |
| --- | --- |
| Daily reset | 100 credits |
| Successful image generation | −25 credits |
| Completed rewarded video | +25 credits |

Credits reset once per local calendar day to exactly 100. Extra credits from ads do not carry past midnight. Credits are **not** deducted if generation fails.

## Project layout

```
app/src/main/java/com/kolpona/ai/
  ads/          Start.io rewarded video
  data/api/     Hugging Face + Cloudflare clients
  data/database Room history
  data/prefs    DataStore (credits, settings)
  data/repository
  domain/manager CreditManager + CreditConfig
  ui/           Compose screens
```

## Configuration cheat sheet

### 1. Generation APIs

- Hugging Face: `HuggingFaceConfig` / `HuggingFaceApiService`
- Cloudflare Workers AI: `CloudflareConfig` / `CloudflareApiService`
- Router: `GenerationRouter` races the two image providers

### 2. Start.io App ID

- **Code:** `app/src/main/java/com/kolpona/ai/ads/AdsConfig.kt` (`APP_ID = "208407601"`)
- **Manifest meta-data:** `app/src/main/AndroidManifest.xml` (`com.startapp.sdk.APPLICATION_ID`)

### 3. Credentials

1. Set keys in **`local.properties`** (gitignored), or
2. Set GitHub Actions secrets `HUGGINGFACE_API_KEY`, `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN`, or
3. Export the same environment variables when assembling.

`app/build.gradle.kts` copies those values into `BuildConfig` at compile time.

### 4. Change daily credits

Edit `DAILY_INITIAL_CREDITS` in:

`app/src/main/java/com/kolpona/ai/domain/manager/CreditConfig.kt`

### 5. Change image-generation cost

Edit `GENERATION_COST` in the same `CreditConfig.kt` file.

### 6. Change rewarded-video reward

Edit `REWARDED_VIDEO_REWARD` in the same `CreditConfig.kt` file.

### 7. Mandatory in-app updates

Each CI build publishes a public `version.json` + APK on the GitHub Release tag `kolpona-release-apk`. The app checks that file on launch. If `versionCode` is higher, a blocking Update screen appears: no Skip / Later / Close. The APK downloads inside the app, then Android’s installer runs. Chat / History / Settings stay locked until the new version is installed.

**Do not add a GitHub token (or any update secret) to the Android app.**

Existing CI secrets (generation only, not updates):

- `HUGGINGFACE_API_KEY`
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`

### 8. Build the Release APK with GitHub Actions

Workflow: `.github/workflows/android.yml`

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
- Authorization is an OkHttp interceptor header. Keys are never added to image URLs, Logcat, errors, or the database.
- HTTPS only (`network_security_config`).

## License

Use and modify for your product. Rotate any credential that was pasted in chat before a public release.
