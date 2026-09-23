# EchoRoute 2.0

EchoRoute is an Android audio-policy experiment that applies platform input preprocessing to newly-created microphone recording sessions. It does **not** create a fake microphone device and it does not inject arbitrary PCM into another application.

## What changed in 2.0

- AEC + NS + AGC selection.
- MIC and VOICE_COMMUNICATION source selection.
- Effect implementation discovery with vendor/implementor information.
- More defensive AudioPolicy method lookup for Android/vendor variations.
- Explicit privilege display: Shizuku UID 2000 (ADB/shell) or UID 0 (root/Sui).
- Event log for operations, warnings, errors, effect IDs, target restart and cleanup.
- Modern dark card-based UI without external UI dependencies.
- Target app selector with package names.
- Adjustable AudioPolicy priority.
- Arabic/English settings entry and reset preferences.
- Built-in explanation screen describing the real mechanism and its HAL limitation.
- Custom vector application icon.
- GitHub Actions debug/release build workflow.
- No analytics, no telemetry and no network runtime dependency.

## Privilege model

EchoRoute does not claim to magically obtain root. It uses the official Shizuku API. In ADB mode, Shizuku normally exposes the Android shell identity (UID 2000); with a root-backed Shizuku/Sui setup the service can run as UID 0. Android permissions, Linux UID/capabilities and SELinux still determine what is actually possible on each firmware.

Official references:
- Shizuku: https://github.com/RikkaApps/Shizuku
- Shizuku API: https://github.com/RikkaApps/Shizuku-API

## Xiaomi / Redmi compatibility strategy

The implementation avoids assuming one vendor effect UUID. It calls `AudioEffect.queryEffects()` and chooses the platform implementation exposed for AEC/NS/AGC. It also searches the AudioPolicy service method by name/parameter count instead of hard-coding one reflected Method object.

This improves portability but cannot guarantee identical behavior across MIUI/HyperOS, Samsung, Qualcomm, MediaTek or other audio HALs. A successful `addSourceDefaultEffect` Binder call means the policy service accepted the request; it is not proof that the vendor DSP is producing the expected acoustic result. The log intentionally reports this distinction.

## Build locally

```bash
chmod +x ./gradlew
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APKs are generated under `app/build/outputs/apk/`.

## Build with GitHub Actions

1. Create an empty GitHub repository.
2. Upload this project.
3. Push to `main` (or run **Actions → Build EchoRoute → Run workflow**).
4. Open the completed workflow run.
5. Download the `echoroute-apks` artifact.

The workflow uses JDK 17, Gradle caching, Android Gradle Plugin 8.7.3 and builds both debug and release variants.

## Important technical limitation

Android's public API does not provide a general interface for one normal application to replace another application's microphone PCM. EchoRoute therefore works through the privileged AudioPolicy source-default-effect path. Whether a selected effect is actually useful depends on the device's AudioPolicy and Audio HAL implementation.

The application deliberately reports failures instead of pretending that an effect is active when the underlying policy call failed.
