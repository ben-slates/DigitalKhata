#!/usr/bin/env bash
# Build and sign a release APK using only this project's Gradle wrapper and local Android SDK.
set -euo pipefail

fail() { printf '\nRelease build error: %s\n' "$1" >&2; exit 1; }

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root_dir"

gradle_version="$(sed -nE 's#.*gradle-([0-9]+\.[0-9]+(\.[0-9]+)?)-bin\.zip.*#\1#p' gradle/wrapper/gradle-wrapper.properties | head -n 1)"
[[ -n "$gradle_version" ]] || fail "Could not determine the Gradle version from the wrapper properties."

version_at_least() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n 1)" == "$2" ]]; }
java_major() { "$1/bin/java" -version 2>&1 | sed -nE '1s/.*"([0-9]+).*/\1/p'; }
java_supports_gradle() {
    case "$1" in
        17|18|19|20|21|22|23) return 0 ;;
        24) version_at_least "$gradle_version" "8.14" ;;
        25) version_at_least "$gradle_version" "9.1" ;;
        *) return 1 ;;
    esac
}

selected_java=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]] && java_supports_gradle "$(java_major "$JAVA_HOME")"; then
    selected_java="$JAVA_HOME"
fi
if [[ -z "$selected_java" ]]; then
    for candidate in /usr/lib/jvm/*; do
        [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]] || continue
        major="$(java_major "$candidate")"
        java_supports_gradle "$major" || continue
        if [[ -z "$selected_java" || "$major" -gt "$(java_major "$selected_java")" ]]; then
            selected_java="$candidate"
        fi
    done
fi
[[ -n "$selected_java" ]] || fail "No compatible installed JDK with javac was found for Gradle $gradle_version."
export JAVA_HOME="$selected_java"
export PATH="$JAVA_HOME/bin:$PATH"

sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android/sdk}}"
build_tools_dir="$sdk_dir/build-tools/35.0.0"
[[ -d "$sdk_dir/platforms/android-35" ]] || fail "Missing Android platform android-35 at $sdk_dir/platforms/android-35."
[[ -x "$sdk_dir/platform-tools/adb" ]] || fail "Missing Android platform-tools at $sdk_dir/platform-tools."
[[ -x "$build_tools_dir/zipalign" && -x "$build_tools_dir/apksigner" ]] || fail "Android Build Tools 35.0.0 must provide zipalign and apksigner."
export ANDROID_HOME="$sdk_dir"
export ANDROID_SDK_ROOT="$sdk_dir"
export PATH="$sdk_dir/platform-tools:$build_tools_dir:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root_dir/.gradle-user-home}"
mkdir -p "$GRADLE_USER_HOME"

[[ -x ./gradlew && -f ./gradle/wrapper/gradle-wrapper.jar ]] || fail "The Gradle wrapper is unavailable."

release_dir="$root_dir/release"
keystore="$release_dir/digital-hisab.jks"
key_alias="digital-hisab"
apk="$release_dir/digital kahata.apk"
mkdir -p "$release_dir"

printf 'Java home: %s\n' "$JAVA_HOME"
printf 'Java: %s\n' "$(java -version 2>&1 | head -n 1)"
printf 'Gradle requested by wrapper: %s\n' "$gradle_version"
printf 'Android SDK: %s\nPlatform: android-35\nBuild tools: 35.0.0\n' "$sdk_dir"

if [[ ! -f "$keystore" ]]; then
    printf '\nNo release signing keystore exists yet. Create it now; keep the passwords and this file safe.\n\n'
    keytool -genkeypair -v -keystore "$keystore" -alias "$key_alias" -keyalg RSA -keysize 2048 -validity 10000
fi

./gradlew --no-daemon assembleRelease

unsigned_apk="$root_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
[[ -f "$unsigned_apk" ]] || fail "Gradle did not produce $unsigned_apk."
aligned_apk="$(mktemp "$release_dir/.digital-hisab-aligned.XXXXXX")"
trap 'rm -f "$aligned_apk"' EXIT

zipalign -p -f 4 "$unsigned_apk" "$aligned_apk"
printf '\nEnter the keystore password to sign the release APK.\n'
apksigner sign --ks "$keystore" --ks-key-alias "$key_alias" --out "$apk" "$aligned_apk"
apksigner verify --verbose --print-certs "$apk"

printf '\nSigned release APK built successfully: %s\n' "$apk"
