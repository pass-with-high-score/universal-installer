# Universal Installer

A modern Android app for installing and managing APK packages with split APK support, silent install via Shizuku, and VirusTotal malware scanning.

## Features

- **Multi-format support** — Install `.apk`, `.apks`, `.xapk`, `.apkm` files
- **APK analysis** — View app name, icon, version, permissions, supported ABIs, languages, and min SDK before installing
- **Split APK handling** — Powered by [Ackpine](https://ackpine.solrudev.ru/) for reliable split package installation
- **Shizuku silent install** — Install/uninstall apps without confirmation prompts (requires [Shizuku](https://shizuku.rikka.app/))
- **VirusTotal scanning** — Automatically scan APKs for malware before installation using the VirusTotal API
- **App manager** — Browse, search, and uninstall installed apps with lazy-loaded high-res icons
- **Material 3 UI** — Dynamic theming with light/dark mode support
- **Intent handling** — Open APK files directly from file managers

## Tech Stack

- **Kotlin** + **Jetpack Compose**
- **Ackpine** — Package install/uninstall with split APK & Shizuku support
- **Shizuku** — Privileged operations via ADB/root
- **Ktor** — HTTP client for VirusTotal API
- **Koin** — Dependency injection
- **DataStore** — Preferences storage
- **Compose Destinations** — Type-safe navigation

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Fastlane

```bash
# Install dependencies
bundle install

# Build debug APK
bundle exec fastlane build_debug

# Build release APK
bundle exec fastlane build_release

# Deploy beta to Firebase App Distribution
bundle exec fastlane beta

# Deploy to Play Store internal track
bundle exec fastlane deploy_internal

# Bump version code
bundle exec fastlane bump_version

# Bump version code + name
bundle exec fastlane bump_version version_name:"2.0"
```

## Configuration

### Shizuku
1. Install [Shizuku](https://shizuku.rikka.app/) on device
2. Start Shizuku service (via ADB or root)
3. Enable "Shizuku Backend" in Settings → grant permission

### VirusTotal
1. Get a free API key from [virustotal.com](https://www.virustotal.com/)
2. Enter key in Settings → Security → VirusTotal API Key
3. APKs will be scanned automatically before installation

## License

MIT
