# GrenobleSki Android Native (Kotlin)

Native Android app in Kotlin + Jetpack Compose, created as a professional replacement for the BeeWare mobile app.

## What is included

- Native Kotlin Android project (`app` module)
- Modern, clean login UI with GrenobleSki branding
- Email/password login against GrenobleSki API
- One-tap browser flows for:
  - Google login
  - Account creation
  - Password reset
- Token session persistence on device
- Dashboard cards (stations, bus lines, services, marketplace)

## API target

The app points to:

- `https://www.grenobleski.fr`

Configured in:

- `app/build.gradle.kts` -> `BuildConfig.API_BASE_URL`

## Open in Android Studio

1. Open Android Studio.
2. `File` -> `Open` -> select `grenobleski_android_native`.
3. Let Gradle sync.
4. Run on a device/emulator.

## Main files

- `app/src/main/java/fr/grenobleski/nativeapp/MainActivity.kt`
- `app/src/main/java/fr/grenobleski/nativeapp/ui/GrenobleSkiApp.kt`
- `app/src/main/java/fr/grenobleski/nativeapp/AppViewModel.kt`
- `app/src/main/java/fr/grenobleski/nativeapp/data/AuthRepository.kt`
- `app/src/main/res/drawable/logo.png`

## Notes

- This project is intentionally in a separate folder to avoid breaking the Django backend or existing BeeWare files.
- If you want, the next step is to migrate all marketplace/messages/instructors screens into native Compose too.
