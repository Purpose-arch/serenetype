# SereneType

Minimalist offline Android keyboard built with Kotlin and Jetpack Compose.

## Features

- **Input engine:** FIFO multi-touch pointer queue, slide-to-select alternate characters, 2D spacebar swipe language switching (`en`, `ru`, `fi`, `de`, `uk`), and batch `InputConnection` edits.
- **Autocorrect & context:** Memory-mapped binary trie dictionary (`.sdict`) with spatial key-distance scoring, phrase bigrams/trigrams, Backspace undo, and optional 50k wordlists downloadable from settings.
- **Smart tap zones:** Optional `off` / `balanced` / `strong` border arbitration that keeps the inner 60% of each key fixed while adapting to your touch offset over time.
- **Utilities:** Configurable top bar (emoji panel, clipboard history, select all, copy, paste, cut, undo), text expander snippets, and custom symbol pages.
- **Appearance:** 5 themes (`ios classic`, `graphite`, `material you`, `oled black`, `custom`), live layout/dimension editor, optional Apple Color Emoji font download, and JSON settings backup/import.

## Download

Grab `SereneType.apk` from the [Releases](../../releases) page, install it on Android 8.0+, and enable it in **System -> Languages & input**.

## Build from source (macOS)

    chmod +x build_and_install.sh && ./build_and_install.sh

The script checks `openjdk@17`, `android-platform-tools`, and Android SDK 35 via Homebrew, builds a release APK with R8, copies it to `dist/SereneType.apk`, and installs it over ADB if a device is connected.

## Project structure

- `ime/` — `SereneInputMethodService`, Compose keyboard UI, vector icons
- `input/` — `PointerTrackerQueue`, `KeyDetector`, `ComposingSession`
- `nlp/` — `BinaryDict` memory-mapped trie and spatial search
- `data/` — `DictionaryEngine`, layouts, DataStore settings, backup/restore, emoji font manager
- `theme/` — theme palettes and hex parser
- `ui/` — settings app and interactive editors

## License

MIT
