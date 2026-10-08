# KaraokeFlow v0.5.0 — SF2 beta and redesigned player

Android native MIDI/KAR playback with **TinySoundFont (MIT)**, selected local SF2 bank, synchronized MIDI lyrics, offline song indexing, favorites, queue, and a dark navy / neon blue UI refresh.

**First launch:** More → Select KaraokeFlow folder, then select or auto-detect `default.sf2` in the folder. Pick a `.mid` or `.kar` song from Songs. A bundled MIDI demo is also included; it requires an SF2 selected for synthesized audio.

**Build:** Upload all files from this ZIP, preserving folders, to the GitHub repository; Actions downloads the MIT-licensed TinySoundFont header and compiles JNI code with NDK/CMake. The `tsf.h` header is intentionally not bundled. Internet access is required by GitHub Actions during the build, not for app playback.

**Limitations:** This is a source-code integration candidate, not a verified installable APK. Android builds and device playback have NOT been tested. TinySoundFont supports standard SF2 (not SF3); SongHub proprietary banks may be unsupported or not licensed for reuse. Android MediaPlayer remains a fallback if no SoundFont is selected and might not support MIDI on some phones. Song number is not shown on the player screen. No SongHub proprietary files or code are included.

**Known pending improvements:** full eight-screen design parity, queue autoplay, persistence of favorites, and performance/compatibility testing on physical devices.
