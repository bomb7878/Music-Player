# 🎵 Music Player

A desktop music player built with Java Swing that supports multiple playlists (tabs), volume control, dark/light theme, shuffle, repeat modes, and keyboard shortcuts.

![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=flat-square&logo=java&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)

---

## Features

- **Multiple playlists** – each playlist is a separate tab
- **ZIP archive support** – automatically extracts `.zip` archives into playlists
- **Playback control** – play, pause, stop, next/previous track
- **Seeking** – skip forward/backward by 10 seconds (keyboard shortcuts)
- **Volume control** – slider with real‑time adjustment
- **Repeat modes** – repeat playlist or single track
- **Shuffle** – randomly reorder tracks (keeps selected track on top)
- **Dark / Light theme** – toggle with one click
- **Keyboard shortcuts**:
  - `Space` – play/pause
  - `→` – skip forward 10 seconds
  - `←` – skip backward 10 seconds

## Getting Started

### Prerequisites

- **Java 17** or higher ([Download](https://adoptium.net/))
- **Git** (for cloning)

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/bomb7878/Music-Player.git
   cd Music-Player
   
2. **Add your music:**

    Place .zip archives (each containing MP3 files) into the music/archives/ folder.
    The application will automatically extract them into separate playlists when launched.

    > **Note:** If you don't have archives, you can also manually create folders inside music/ and place MP3 files directly.

## Project Structure
```text
  music-player/
  ├── src/                    # Source code
  │   ├── Window.java         # Main GUI window
  │   ├── MusicPlayer.java    # Core playback logic
  │   ├── MusicAudioDevice.java # Volume control
  │   ├── Server.java         # Archive extraction utility
  │   ├── Colors.java         # Theme colors
  │   └── util/               # Utility classes (SwingUtils)
  ├── lib/                    # External JAR dependencies
  ├── music/                  # Music directory (user data)
  │   ├── archives/           # Place ZIP archives here
  │   │   └── .gitkeep
  ├── icons/                  # Icons for buttons
  │   ├── play_track.png
  │   └── stop_track.png
  ├── .gitignore              # Ignored files
  └── README.md
```

##  Dependencies
**All dependencies are provided as JAR files in the libs/ folder.**

| Library |	Version |	Description |	Link |
| :-----: | :---: | :---------: | :--------: |
| JLayer	| 1.0.1 |	MP3 decoding and playback |	[Link](https://mvnrepository.com/artifact/javazoom/jlayer/1.0.1) |
| JAudioTagger	| 3.0.1 |	Editing tag information in audio files	| [Link](https://mvnrepository.com/artifact/net.jthink/jaudiotagger/3.0.1) |
| Apache Commons Compress |	1.28.0	| API for working with compression and archive formats | [Link](https://mvnrepository.com/artifact/org.apache.commons/commons-compress/1.28.0) |
| Apache Commons IO | 2.21.0 | Stream implementations, file filters, file comparators, endian transformation classes, and much more | [Link](https://mvnrepository.com/artifact/commons-io/commons-io/2.21.0) |
| Apache Commons Lang | 3.14.0 | Utility classes for the classes that are in java.lang's hierarchy, or are considered to be so standard as to justify existence in java.lang | [Link](https://mvnrepository.com/artifact/org.apache.commons/commons-lang3/3.14.0) |

> **Tip:** If you use Maven or Gradle, you can replace manual JAR management with dependency declarations. However, this project is designed to work without a build system.

## Usage
  Launch the application – the main window opens with an empty playlist.
  
  Add music – place .zip archives in music/archives/ or create folders in music/ with MP3 files.
  
  Restart the application – the playlists will appear as tabs.
  
  Select a track – double-click (or single‑click) a song to start playback.
  
  Control playback – use the buttons or keyboard shortcuts.
  
  Adjust volume – click the volume icon to show/hide the slider.
  
  Toggle theme – click the theme button to switch between dark and light modes.
  
  Enable repeat/shuffle – use the corresponding checkboxes.

## Contact
  Bomb7878 – nikita785nikit@gmail.com
  
  GitHub: [@bomb7878](https://github.com/bomb7878)
