# ClipForge Auto-Editor

Production-ready Android app in **Kotlin + Jetpack Compose (Material 3)** for assembling 16:9 YouTube compilation videos (e.g., *Animation vs Real Footage*) locally on-device using **FFmpegKit** and **Media3 ExoPlayer**.

## Features
- **AMOLED Dark UI**: `#000000` background, `#121212` cards, `#FF2E4C` crimson accent.
- **AI Script Mode**: Paste JSON Story Pairs (or load the sample template), tap missing video slots via Android Scoped Storage (`OpenDocument`), and render.
- **Visual Pair Builder**: Drag-to-reorder Story Pairs, toggle 9:16-to-16:9 framing (`Blurred 16:9 Fill` vs `Left + Teaser Card`), add/trim multiple real-footage angles, configure `Location & Year`, `Shock Fact`, `Outcome Badge` (`SURVIVED` / `TRAGIC` / `NONE`), optional `Credit`, channel watermark, synthesized transition SFX (`Whoosh`, `Boom`, `Shutter`), and background music auto-ducking (`sidechaincompress`).
- **Render & Export**: Live FFmpegKit frame/time/speed/ETA progress, hardware `h264_mediacodec` with automatic `libx264` fallback, debug command viewer + copy, built-in ExoPlayer 16:9 preview, `Movies/ClipForge/` MediaStore export, and `Share to YouTube`.

## Build Instructions
1. Open the `ClipForgeAutoEditor` folder in **Android Studio Ladybug (2024.2+)** or newer.
2. Ensure JDK 17 is selected for Gradle.
3. Sync Gradle and run on a physical Android device (`minSdk 26`, `arm64-v8a` / `armeabi-v7a` / `x86_64`).
   - *Note*: `app/build.gradle.kts` uses `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7` (Maven Central).

## Codemagic (free)
`codemagic.yaml` must sit at the repository root (next to `app/`). It builds a debug APK on `mac_mini_m2` (500 free min/month on personal accounts) using a Gradle 8.14.3 download, so no `gradle-wrapper.jar` is needed.

## Render pipeline
Each clip is rendered in its own small FFmpeg run (input-level seeking, one decoder at a time) and the segments are joined with the concat demuxer (stream copy). Use **Attach ALL videos at once** on the AI Script tab: files are sorted by name and filled in script order.
