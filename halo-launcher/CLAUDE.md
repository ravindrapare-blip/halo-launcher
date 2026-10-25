# Halo launcher — notes for Claude Code

Android TV home-screen app (Kotlin, Jetpack Compose foundation only — no Material, no tv-material).
Package `com.halo.launcher`, minSdk 26, compileSdk 35, AGP 8.7.3, Kotlin 2.0.21, Gradle 8.10.2.

## Build
- `./gradlew assembleRelease` (signed with the checked-in `app/halo-sideload.jks`).
- This code was written without a compiler available, so the first build may surface small
  compile errors (an import, a named argument). Fix them minimally; do not restructure.

## Rules
- All visual values come from `ui/theme/Tokens.kt` (HaloColor, HaloGlass, HaloRadius, HaloSpace,
  HaloSize, HaloType, HaloMotion). Don't hard-code new colours or sizes in screens — add a token.
- Every focusable thing uses `HaloFocusable` (ui/components/Primitives.kt): D-pad focus, OK click,
  hold-OK long press, spring scale, ring/glow. Don't use `Modifier.clickable` for TV targets.
- Layout units assume a 960×540 dp TV canvas.
- Resume logic lives only in `resume/ResumeEngine.kt`; media capture only in
  `service/MediaListenerService.kt`. Keep that separation.
- Keep the Watch Next query defensive: it throws SecurityException on most TVs — that's expected.

## Test on a TV / emulator
`adb install -r app/build/outputs/apk/release/app-release.apk` then
`adb shell cmd notification allow_listener com.halo.launcher/com.halo.launcher.service.MediaListenerService`.
An "Android TV (1080p)" emulator image works for UI; play something in a media app to see the row fill.
