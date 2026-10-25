# Xparty Android 1.2

Android 8 or newer, system WebView required. The shared XParty web interface runs alongside native ExoPlayer video, room lighting, Quick Search, local-file playback and IPTV panels.

Accounts use the existing Supabase email/phone flow and database through the shared WebView interface. There is no Firebase SDK, separate native account store, or Google sign-in screen. Protected profiles, friendships and private messages use the same verified session and database RLS as the web version. The APK opens https://pkxparty.onrender.com; local web edits require deployment to appear there. Physical-device validation is required before release.

Native integration includes camera/microphone runtime consent, document picking without broad storage permissions, room sharing, fullscreen, system PiP, and connection retry. Camera and microphone access are requested only when used. Keep the existing private signing key for application updates.

Build with Java 17 or 21, Android platform 35 and build tools 35.0.0. Run `./gradlew :app:assembleDebug` (Windows: `gradlew.bat`) for local testing. For release, set XPARTY_ANDROID_SDK, XPARTY_KEYSTORE and XPARTY_STORE_PASS, then run `./build-apk.sh`. CI builds debug APKs and uses the existing release key only when configured; it never generates a new release identity. Package com.pkx404.xparty, version code 12.

Gradle includes ExoPlayer 2.19.1, NewPipeExtractor, Glide, Material panels and all Kotlin components. The former standalone Kotlin compiler script is obsolete. Native YouTube extraction and physical-device playback require separate runtime validation; a successful APK build does not establish provider playback support or startup timing.

The globe geography in `app/src/main/assets/world-countries.geojson` is Natural Earth's public-domain 1:110m countries dataset, sourced from https://github.com/nvkelso/natural-earth-vector.
