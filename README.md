# Digital Hisab

Digital Hisab is a simple, offline Android Khata app for tracking the amounts you pay to people. It keeps a clear payment history for every person and shows your total paid amount and remaining budget.

All data stays on the device. The app does not require an account, internet connection, or cloud service to track payments.

## Features

- Add a person and record a payment in one step
- Record multiple payments for the same person
- View each person's total and payment history
- Edit or delete individual payments
- Delete a person and their associated payments
- Browse all payments grouped by day
- Set a total budget and see total paid and remaining amounts
- Search people by name
- Choose system, light, or dark theme
- Navigate with the bottom bar or swipe between main pages
- Fully offline local storage

## Screens

- **Home** — budget, total paid, remaining amount, and recent payments
- **People** — searchable list of people and the total paid for each one
- **Person details** — payment history for one person, with add, edit, and delete actions
- **Daily history** — payments grouped by date
- **Settings** — budget and theme preferences

## Offline data storage

Digital Hisab stores its data locally on the device with Room, Android's SQLite persistence library. Payment records, people, and the budget are available offline and are not sent to a server.

## Tech stack

- Kotlin
- Jetpack Compose with Material 3
- Android Gradle Plugin 9.0.1
- Gradle 9.1.0 (project wrapper)
- Room database with KSP
- Navigation Compose
- compileSdk 35 / targetSdk 35
- Minimum Android version: Android 6.0 (API 23)

## Requirements

To build on Linux, including Kali Linux, you need the following already installed:

- A compatible JDK. This project is configured for Java 25 with Gradle 9.1.0.
- Android SDK at `$HOME/android/sdk`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT` set to your SDK location.
- Android SDK Platform 35: `$HOME/android/sdk/platforms/android-35`
- Android Build Tools 35.0.0: `$HOME/android/sdk/build-tools/35.0.0`
- Android platform-tools: `$HOME/android/sdk/platform-tools`

The project uses its own Gradle wrapper; a globally installed `gradle` command is not required.

## Build on Linux / Kali Linux

From the project folder:

```bash
chmod +x build.sh
./build.sh
```

`build.sh` detects Java, configures `ANDROID_HOME` and `ANDROID_SDK_ROOT` when needed, validates the required SDK components, and builds the debug APK through `./gradlew`.

If your SDK is in a different location, set it for the current terminal before building:

```bash
export ANDROID_SDK_ROOT="/path/to/android/sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./build.sh
```

## Generate the debug APK

The normal command is:

```bash
./build.sh
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on an Android device with ADB

Enable Developer options and USB debugging on the device, connect it, then run:

```bash
"$HOME/android/sdk/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

If `adb` is already on your `PATH`, this also works:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Basic usage

1. Open **Settings** and set your total budget if you want to track the remaining amount.
2. Tap **+** on Home to enter a person's name, amount, and an optional note.
3. Open **People** to see each person's total.
4. Select a person to add more payments or edit/delete existing ones.
5. Use **Daily** to review payments by date.

## Project structure

```text
.
├── app/
│   ├── src/main/java/com/ben/khata/
│   │   ├── MainActivity.kt          # Compose UI and navigation
│   │   └── data/KhataDatabase.kt    # Room entities, DAO, and database
│   ├── src/main/res/                # App resources and logo
│   └── build.gradle.kts             # App module configuration
├── gradle/wrapper/                  # Gradle wrapper configuration
├── build.gradle.kts                 # Root build configuration
├── settings.gradle.kts              # Project settings
└── build.sh                         # Linux build script
```

## Screenshots

Screenshots coming soon.

## License

No license has been selected yet.
