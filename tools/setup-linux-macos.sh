#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$ROOT/tools/portable"
mkdir -p "$TOOLS"
OS="$(uname -s)"
ARCH="$(uname -m)"
case "$OS" in
  Linux) PLATFORM="linux"; JDK_OS="linux" ;;
  Darwin) PLATFORM="mac"; JDK_OS="mac" ;;
  *) echo "Unsupported OS: $OS"; exit 1 ;;
esac
case "$ARCH" in
  x86_64|amd64) JDK_ARCH="x64" ;;
  aarch64|arm64) JDK_ARCH="aarch64" ;;
  *) echo "Unsupported CPU architecture: $ARCH"; exit 1 ;;
esac

echo "1/4: Downloading portable JDK 17..."
JDK_ZIP="$TOOLS/jdk17.tar.gz"
curl -fL "https://api.adoptium.net/v3/binary/latest/17/ga/${JDK_OS}/${JDK_ARCH}/jdk/hotspot/normal/eclipse" -o "$JDK_ZIP"
rm -rf "$TOOLS/jdk" "$TOOLS/jdk-unpack"; mkdir -p "$TOOLS/jdk-unpack"
tar -xzf "$JDK_ZIP" -C "$TOOLS/jdk-unpack"
mv "$TOOLS/jdk-unpack"/* "$TOOLS/jdk"
rm -rf "$TOOLS/jdk-unpack" "$JDK_ZIP"

echo "2/4: Downloading Gradle 8.9..."
curl -fL "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$TOOLS/gradle.zip"
rm -rf "$TOOLS/gradle-unpack" "$TOOLS/gradle-8.9"; mkdir -p "$TOOLS/gradle-unpack"
unzip -q "$TOOLS/gradle.zip" -d "$TOOLS/gradle-unpack"
mv "$TOOLS/gradle-unpack/gradle-8.9" "$TOOLS/gradle-8.9"
rm -rf "$TOOLS/gradle-unpack" "$TOOLS/gradle.zip"

echo "3/4: Downloading Android SDK command-line tools (sdkmanager)..."
if [ "$PLATFORM" = "linux" ]; then CLI_ZIP="commandlinetools-linux-11076708_latest.zip"; else CLI_ZIP="commandlinetools-mac-11076708_latest.zip"; fi
curl -fL "https://dl.google.com/android/repository/$CLI_ZIP" -o "$TOOLS/android-cli.zip"
rm -rf "$TOOLS/sdk-unpack"; mkdir -p "$TOOLS/sdk-unpack" "$TOOLS/android-sdk/cmdline-tools/latest"
unzip -q "$TOOLS/android-cli.zip" -d "$TOOLS/sdk-unpack"
cp -R "$TOOLS/sdk-unpack/cmdline-tools/." "$TOOLS/android-sdk/cmdline-tools/latest/"
rm -rf "$TOOLS/sdk-unpack" "$TOOLS/android-cli.zip"

export JAVA_HOME="$TOOLS/jdk"
export ANDROID_HOME="$TOOLS/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$TOOLS/gradle-8.9/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
echo "4/4: Installing Android SDK platform/build tools..."
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo
echo "Setup complete."
echo "JDK: $JAVA_HOME"
echo "Gradle: $TOOLS/gradle-8.9"
echo "Android SDK / sdkmanager: $ANDROID_HOME"
echo "Build with:"
echo "  JAVA_HOME='$JAVA_HOME' ANDROID_HOME='$ANDROID_HOME' ANDROID_SDK_ROOT='$ANDROID_HOME' '$TOOLS/gradle-8.9/bin/gradle' -p '$ROOT' assembleDebug"
