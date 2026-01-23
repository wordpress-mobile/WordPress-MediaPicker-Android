# mediapicker

The core MediaPicker library module providing media selection functionality for Android applications.

## Tenor API Deprecation Notice

> [!WARNING]
> The Tenor API used by the `source-gif` module is being sunset on **June 30, 2026**. This also affects references in this parent module.

This module contains Tenor-related references that will need to be updated when migrating to an alternative GIF provider:

| File | Reference |
|------|-----------|
| `src/main/res/menu/media_picker_lib_menu.xml` | Menu item ID `mnu_choose_from_tenor_library` |
| `src/main/java/org/wordpress/android/mediapicker/ui/MediaPickerFragment.kt` | `tenorLibraryMenuItem` variable |
| `src/main/res/values/strings.xml` | String `photo_picker_gif` with value "Choose from Tenor" |

For full deprecation details, migration recommendations, and steps, see the [source-gif module README](source-gif/README.md).

## Module Structure

The mediapicker library is organized into the following modules:

| Module | Description |
|--------|-------------|
| `mediapicker` | Core UI and picker functionality |
| `domain` | Domain models and interfaces |
| `source-device` | Device storage media source |
| `source-camera` | Camera capture media source |
| `source-gif` | GIF search media source (Tenor API - **deprecated**) |
| `source-wordpress` | WordPress media library source |
