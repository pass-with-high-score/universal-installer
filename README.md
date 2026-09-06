<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" height="128" alt="Universal Installer Icon">
  <h1>Universal Installer</h1>
  <p>A modern, powerful Android package manager and sideloading tool for Phones, Android TV, and Wear OS.</p>

  <p>
    <a href="https://github.com/pass-with-high-score/universal-installer/releases">
      <img src="https://img.shields.io/github/v/release/pass-with-high-score/universal-installer" alt="Latest Release">
    </a>
    <a href="https://github.com/pass-with-high-score/universal-installer/releases">
      <img src="https://img.shields.io/github/downloads/pass-with-high-score/universal-installer/total" alt="Downloads">
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-GPL--3.0--only-blue.svg" alt="License">
    </a>
  </p>

  <h4>Download</h4>
  <p>
    <a href="https://play.google.com/store/apps/details?id=app.pwhs.universalinstaller">
      <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="55" alt="Google Play">
    </a>
    <a href="https://f-droid.org/packages/app.pwhs.universalinstaller">
      <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="55" alt="F-Droid">
    </a>
    <a href="https://apt.izzysoft.de/fdroid/index/apk/app.pwhs.universalinstaller">
      <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="55" alt="IzzyOnDroid">
    </a>
    <a href="https://github.com/pass-with-high-score/universal-installer/releases">
      <img src="https://raw.githubusercontent.com/NeoApplications/Neo-Backup/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" height="55" alt="GitHub Release">
    </a>
  </p>

  <a href="https://universal-installer.pwhs.app/">Website</a> ·
  <a href="https://universal-installer.pwhs.app/privacy">Privacy</a> ·
  <a href="https://universal-installer.pwhs.app/terms">Terms</a>
</div>

---

## Supported Platforms

| Platform | Module | Description |
| :--- | :--- | :--- |
| 📱 **Phone & Tablet** | `:app` | Material 3 UI, dynamic colors & spring animations |
| 📺 **Android TV** | `:tv` | D-pad navigation, 10-foot UI with QR code pairing & remote receive |
| ⌚ **Wear OS** | `:wearos` | Wear Compose UI for smartwatch package management & install |

---

## Supported Formats

- **Single APK**: `.apk`
- **Split APK Bundles**: `.apks`, `.xapk`, `.apkm`, `.apk+`
- **Manual Splits**: Merge multiple individual `.apk` files into a single installation session
- **Game Data**: Automatic extraction & background placement of `.obb` expansion files

---

## Key Features

- **⚡ Flexible Installation Backends**:
  - Standard system installer, **Shizuku**, and **Root (libsu)**.
  - Silent install and uninstall without prompts.
  - Privileged controls: allow downgrade, bypass minimum SDK restrictions, grant permissions, install for all users, and spoof installer package names (Google Play, F-Droid, Aurora Store, Amazon, etc.).

- **📦 Seamless OBB Support**:
  - Auto-extracts `.obb` files from XAPK/APKM archives directly to `Android/obb/<package>/`.
  - Attach standalone OBB files to APK installs.
  - Multi-tier write strategy: Direct I/O, Shizuku, or Storage Access Framework (SAF).

- **🛡️ VirusTotal Security**:
  - Automatic SHA-256 hash lookup before install.
  - Engine detection breakdown (malicious, suspicious, clean).
  - On-demand file upload scan for unindexed packages (up to 650 MB).

- **🌐 Local Sharing & Send to TV**:
  - Built-in HTTP server and web dashboard for transferring APKs from PC or mobile browser.
  - Push packages directly to Android TV via QR code scanning.
  - Optional PIN security for local network access.

- **🗂️ App Manager**:
  - Inspect installed user & system apps with rich details (SDK targets, size, permissions, signatures).
  - Sort and filter by name, install date, size, or last used time.
  - Batch uninstall with detailed status logs.

- **📥 Remote Downloader & Intake**:
  - Download APK packages directly from web URLs with download history.
  - Deep-link and intent handling to open packages from browsers, messaging apps, and file managers.

---

## Tech Stack

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose), [Wear Compose](https://developer.android.com/training/wearables/compose), Material 3
- **Installer Engine**: [Ackpine](https://ackpine.solrudev.ru/) & [Shizuku](https://shizuku.rikka.app/)
- **Networking**: [Ktor](https://ktor.io/)
- **Storage & Async**: [Room](https://developer.android.com/training/data-storage/room), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore), [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Dependency Injection**: [Koin](https://insert-koin.io/)

---

## Project Structure

```text
├── app/        # Android Phone & Tablet application
├── tv/         # Android TV application
├── wearos/     # Wear OS smartwatch application
├── core/       # Shared engine (package parsing, installers, storage, networking)
└── updater/    # In-app update checking logic
```

---

## Building from Source

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17+
- Android SDK 36

### Build Commands
```bash
# Build Phone app (open-source flavor)
./gradlew :app:assembleOpensourceDebug

# Build Android TV app
./gradlew :tv:assembleDebug

# Build Wear OS app
./gradlew :wearos:assembleDebug

# Build all debug variants
./gradlew assembleDebug
```

> **Note:** The `play` flavor of `:app` requires `app/src/play/google-services.json` (see [docs/FIREBASE.md](docs/FIREBASE.md)). Without it, build the `opensource` flavor.

---

## Contributing & License

- **Issues & Requests**: [GitHub Issues](https://github.com/pass-with-high-score/universal-installer/issues)
- **License**: [GNU General Public License v3.0 (GPL-3.0)](LICENSE)
- **Maintainer**: [Nguyen Quang Minh](https://github.com/nqmgaming)
