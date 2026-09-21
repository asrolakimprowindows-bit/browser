#!/usr/bin/env bash
# One-shot Android debug build for GitHub Codespaces.
# Installs JDK 17 + Android SDK (compileSdk 35) if missing, then runs assembleDebug.
# Usage: bash android/codespaces-build.sh
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$REPO_DIR/android"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

log "Checking JDK 17"
if ! ls -d /usr/lib/jvm/java-17-openjdk-* >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless unzip curl
fi
export JAVA_HOME="$(ls -d /usr/lib/jvm/java-17-openjdk-* | head -1)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1

log "Checking Android SDK at $ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -sSL -o /tmp/cmdline-tools.zip "$CMDLINE_TOOLS_URL"
  rm -rf /tmp/cmdline-tools && mkdir -p /tmp/cmdline-tools
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -d "$ANDROID_HOME/platforms/android-35" ] || [ ! -d "$ANDROID_HOME/build-tools/35.0.0" ]; then
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null
fi

log "Writing android/local.properties"
echo "sdk.dir=$ANDROID_HOME" > "$ANDROID_DIR/local.properties"

log "Persisting JAVA_HOME / ANDROID_HOME to ~/.bashrc"
if ! grep -q "MULTEX_ANDROID_ENV" "$HOME/.bashrc" 2>/dev/null; then
  cat >> "$HOME/.bashrc" <<EOF

# MULTEX_ANDROID_ENV
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF
fi

log "Building debug APK"
cd "$ANDROID_DIR"
chmod +x gradlew
./gradlew --stop >/dev/null 2>&1 || true
./gradlew assembleDebug --no-daemon --console=plain

APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
log "Done"
ls -lh "$APK"
echo
echo "APK: $APK"
echo "Download: di Explorer VS Code buka android/app/build/outputs/apk/debug/ -> klik kanan app-debug.apk -> Download"
