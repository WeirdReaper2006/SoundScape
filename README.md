<div align="center">

<br/>

# 🎵 SoundScape

**An offline music player for Android. Beautiful. Private. Yours.**

[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-gray?style=flat-square)](LICENSE)

</div>

---

## What is SoundScape?

SoundScape is a fully offline, local music player for Android built with a deep Spotify-inspired dark aesthetic. It scans your device for audio files and plays them back with a clean, distraction-free experience — no internet required, no tracking, no ads. Your library stays on your device.

---

## Features

- **Offline-first** — plays from your local storage, no account required
- **Broad format support** — FLAC, MP3, and M4A out of the box
- **Playlist management** — create, edit, and organize playlists
- **Background playback** — audio keeps playing when the screen is off or you switch apps
- **Dark Spotify-inspired UI** — polished, immersive design built entirely with Jetpack Compose

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |

---

## Getting Started

### Installation

To install SoundScape on your Android device:

1. **Download the APK**: Go to the **Releases** page on GitHub and download the latest `SoundScape.apk`.
2. **Install the APK**: Locate the downloaded APK using a File Manager on your Android device, tap it, and follow the prompts to install (you may need to enable "Install from Unknown Sources" if prompted).
3. **Enjoy your music**: Open SoundScape from your app drawer and enjoy a private, offline listening experience.

---

## Project Structure

```
SoundScape/
├── app/                    # Main application module
│   └── src/
│       ├── main/
│       │   ├── java/       # Kotlin source files
│       │   └── res/        # Resources (layouts, drawables, strings)
│       └── test/           # Unit & screenshot tests
├── gradle/                 # Gradle wrapper & version catalog
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Module settings
└── metadata.json           # App metadata
```
---

## License

This project is open source under the [MIT License](LICENSE).

---

<div align="center">

Built with Kotlin · Jetpack Compose

*No streams. No subscriptions. Just your music.*

</div>
