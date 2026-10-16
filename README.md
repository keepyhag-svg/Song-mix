# MetroMix — Minimalist 40s/20s Multi-Song YouTube Music Remix Client (Kotlin)

Built on the **Metrolist** (`com.metrolist.innertube`) architecture in 100% Kotlin + Jetpack Compose + AndroidX Media3 (`ExoPlayer`).

## Key Capabilities
1. **Batch 2–5 Song Remix Crate**: Select between 2 and 5 tracks simultaneously from the YouTube Music free catalog.
2. **Authentic 40s Anchor / 20s Hook Remix Engine**:
   - Alternates **40-second Anchor segments** (verse/groove progression) and **20-second Hook segments** (peak chorus/drop) across your 2–5 selected tracks.
   - **Phrase-Aligned Entry Points**: Automatically skips cold intros and fade-out tails, snapping segment entry points to 4/4 musical phrase boundaries (`PhraseAlignedRemixPlanner`) and advancing each track's cursor on subsequent cycles.
   - **Dual-ExoPlayer Equal-Power Crossfade + Bass Swap**: Pre-buffers and cues the incoming song 8 seconds ahead on a standby `ExoPlayer` deck, executing a 3.6-second constant-power ($\cos/\sin$) crossfade with a 60Hz–230Hz low-cut bass swap on the midpoint downbeat and YouTube `loudnessDb` gain normalization.
3. **Zero-Error InnerTube Free Stream Scraper**:
   - Uses Metrolist's multi-client fallback chain (`ANDROID_VR` → `IOS` → `ANDROID_NO_SDK`) and a custom Media3 `ResolvingDataSource` that injects client-matched `User-Agent` headers to prevent `signatureCipher` locks and HTTP 403 playback errors.
4. **Non-AI Industrial Minimalist UI**:
   - Inspired by Braun hardware and Swiss typographic grids (`#111110` Carbon / `#F2F0EA` Archival Paper, `1.dp` hairline rules, tabular monospace telemetry, and zero neon/purple gradients).

## How to Get the Installable `.apk`
- **Option A — Cloud 1-Click APK Build (No Android Studio Needed)**:
  Upload or push this folder to any free GitHub repository. The included `.github/workflows/build-apk.yml` workflow automatically runs Gradle in the cloud and publishes `app-release.apk` under the **Actions → MetroMix-Installable-APK** download tab in ~2 minutes.
- **Option B — Local Build (Android Studio / CLI)**:
  Open this project in Android Studio Ladybug+ (or run `gradle assembleRelease`) to generate `app/build/outputs/apk/release/app-release.apk`.
