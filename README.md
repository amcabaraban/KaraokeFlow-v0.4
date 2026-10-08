# KaraokeFlow v0.4 — first implementation package

This package contains the **first implemented, engine-independent component** of the approved GUI redesign: `LibraryController.java`.

Implemented:
- Song metadata model (song number, title, artist, MIDI path)
- Case-insensitive title/artist/number search with result limit
- Add/remove/clear song queue
- Favorites toggling and favorites list
- Thread-safe state operations

Not yet implemented:
- New Android Activity/layout and navigation screens
- MIDI filesystem index and persistence
- Integration with v0.3's native TinySoundFont player and lyric timeline
- Buildable APK

**Important:** The GitHub `main` branch currently has a catalog-only `MainActivity.java`, while the uploaded v0.3 APK has a different compiled player. Do not overwrite the working v0.3 source with that older file. Merge this controller into the matching v0.3 project when available.

GitHub connector rejected write access (HTTP 403), so this package was not pushed to the repository.
