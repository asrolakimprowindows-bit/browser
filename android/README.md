# Multex × Denia — Android

Native Jetpack Compose port of the web preview: a WebView browser with the Denia companion
(tap = talk, drag = move, double-tap = direct mode) drawn above the page.

## Denia AI

In **Settings → Denia AI**, choose either **Gemini** or **OpenAI compat.**.

For an OpenAI-compatible provider, enter:

- **Base API URL** — normally ending in `/v1`, e.g. `https://api.example.com/v1`; a complete
  `/chat/completions` URL also works.
- **API key** — optional for local servers.
- **Model** — the model ID offered by that provider.

Denia uses the standard Chat Completions request format. Her offline commands and official-site
lookup continue to work without any remote AI configuration.

## Build (GitHub Actions — recommended, zero setup)

1. Push this repo to GitHub.
2. Open the **Actions** tab → **Android APK** → **Run workflow** (it also runs on every push under `android/`).
3. Download the `multex-debug-apk` artifact and install `app-debug.apk` on your phone.

## Build (GitHub Codespaces / any Linux)

```bash
# Java 17
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless unzip

# Android SDK (command-line tools only)
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools
curl -Lo /tmp/cmdline.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdline.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# Build
cd android
chmod +x gradlew
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Build (Android Studio)

Open the `android/` folder as a project, let Gradle sync, then **Run**.

## Layout

```
app/src/main/java/com/multex/browser/
  MainActivity.kt        entry point, handles http/https intents
  BrowserScreen.kt       WebView + glass toolbar + home screen
  SettingsSheet.kt       companion + search engine settings
  BrowserData.kt         shortcuts, pets, sizes, engines
  Theme.kt               Material 3 dark palette
  denia/DeniaCompanion.kt  the companion overlay
app/src/main/res/drawable/  Denia poses, faces and pets (from public/denia)
```
