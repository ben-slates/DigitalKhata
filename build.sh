#!/usr/bin/env bash
# Build Khata on Linux using the project's Gradle wrapper and the installed Android SDK.
set -euo pipefail

fail() { printf '\nBuild error: %s\n' "$1" >&2; exit 1; }
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$root_dir"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root_dir/.gradle-user-home}"
mkdir -p "$GRADLE_USER_HOME"

gradle_version="$(sed -nE 's#.*gradle-([0-9]+\.[0-9]+(\.[0-9]+)?)-bin\.zip.*#\1#p' gradle/wrapper/gradle-wrapper.properties | head -n 1)"
[[ -n "$gradle_version" ]] || fail "Could not determine the Gradle version from gradle/wrapper/gradle-wrapper.properties."

version_at_least() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -n 1)" == "$2" ]]; }
java_supports_gradle() {
  local major="$1"
  case "$major" in
    17|18|19|20|21|22|23) return 0 ;;
    24) version_at_least "$gradle_version" "8.14" ;;
    25) version_at_least "$gradle_version" "9.1" ;;
    *) return 1 ;;
  esac
}
java_major() { "$1/bin/java" -version 2>&1 | sed -nE '1s/.*"([0-9]+).*/\1/p'; }

# Prefer a compatible JAVA_HOME, otherwise inspect installed JVMs and select the newest one.
selected_java=""
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] && java_supports_gradle "$(java_major "$JAVA_HOME")"; then
  selected_java="$JAVA_HOME"
fi
if [[ -z "$selected_java" ]]; then
  for candidate in /usr/lib/jvm/*; do
    [[ -x "$candidate/bin/java" ]] || continue
    major="$(java_major "$candidate")"
    java_supports_gradle "$major" || continue
    if [[ -z "$selected_java" || "$major" -gt "$(java_major "$selected_java")" ]]; then selected_java="$candidate"; fi
  done
fi
[[ -n "$selected_java" ]] || fail "Gradle $gradle_version has no compatible installed JDK. Gradle 9.1+ supports Java 25; older Gradle versions do not."
export JAVA_HOME="$selected_java"
export PATH="$JAVA_HOME/bin:$PATH"
printf 'Java home: %s\n' "$JAVA_HOME"
printf 'Java: %s\n' "$(java -version 2>&1 | head -n 1)"
printf 'Gradle requested by wrapper: %s\n' "$gradle_version"
printf 'Gradle user home: %s\n' "$GRADLE_USER_HOME"

# Kali's standard per-user SDK location is preferred if environment variables are unset.
sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android/sdk}}"
[[ -d "$sdk_dir" ]] || fail "Android SDK was not found at $sdk_dir. Set ANDROID_SDK_ROOT or ANDROID_HOME if it is elsewhere."
export ANDROID_SDK_ROOT="$sdk_dir"
export ANDROID_HOME="$sdk_dir"
build_tools_dir="$sdk_dir/build-tools/35.0.0"
export PATH="$sdk_dir/platform-tools:$build_tools_dir:$PATH"
printf 'Android SDK: %s\n' "$sdk_dir"
[[ -x "$sdk_dir/platform-tools/adb" ]] || fail "Missing Android platform-tools at $sdk_dir/platform-tools."
[[ -d "$build_tools_dir" ]] || fail "Missing Android Build Tools 35.0.0."
[[ -d "$sdk_dir/platforms/android-35" ]] || fail "Missing Android platform android-35."
[[ -x "$build_tools_dir/aapt2" ]] || fail "Android Build Tools 35.0.0 is incomplete: aapt2 is missing."
printf 'Platform: android-35\nBuild tools: %s\n' "$(basename "$build_tools_dir")"

[[ -x ./gradlew && -f ./gradle/wrapper/gradle-wrapper.jar ]] || fail "The Gradle wrapper is unavailable: expected ./gradlew and ./gradle/wrapper/gradle-wrapper.jar. No usable local Gradle distribution exists, and this script will not download one."
./gradlew --no-daemon assembleDebug

apk="$root_dir/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || fail "Gradle finished without producing the expected debug APK."
printf '\nAPK built successfully: %s\n' "$apk"
