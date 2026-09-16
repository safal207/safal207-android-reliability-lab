#!/usr/bin/env bash
set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-35}"
ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

if command -v sdkmanager >/dev/null 2>&1; then
  SDKMANAGER="$(command -v sdkmanager)"
elif [[ -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]]; then
  SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
else
  echo "ERROR: sdkmanager was not found." >&2
  echo "Install Android command-line tools or use the repository Dockerfile." >&2
  exit 1
fi

packages=(
  "platform-tools"
  "platforms;android-${ANDROID_API_LEVEL}"
  "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"
)

echo "==> Android SDK root: $ANDROID_SDK_ROOT"
echo "==> Installing: ${packages[*]}"

# sdkmanager exits successfully once all accepted licenses are already present.
# `yes` may receive SIGPIPE after sdkmanager finishes, so license acceptance is
# intentionally tolerant while the actual package installation remains strict.
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "${packages[@]}"

echo
printf '%s\n' "==> Toolchain evidence"
java -version 2>&1
printf 'sdkmanager: '
"$SDKMANAGER" --version
adb --version | head -n 2

if [[ "${1:-}" == "--sdk-only" ]]; then
  echo "==> SDK environment ready. Application build was not requested."
  exit 0
fi

if [[ ! -f ./gradlew ]]; then
  echo "ERROR: ./gradlew is absent." >&2
  echo "The Android SDK environment is ready, but no application build can be claimed yet." >&2
  echo "Create the Gradle Android project in Bead 001, then rerun this script." >&2
  exit 2
fi

chmod +x ./gradlew

echo "==> Running application verification"
./gradlew --no-daemon test lint assembleDebug

echo "==> Application verification complete"
find . -path '*/build/outputs/apk/*' -type f -name '*.apk' -print 2>/dev/null || true
