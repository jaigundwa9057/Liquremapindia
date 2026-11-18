# LiquorMap India — Android source project

A starter Android app (Kotlin) that requests the user's location, searches OpenStreetMap/Overpass for mapped `shop=alcohol` / `shop=wine` features within 10 km, shows a Leaflet/OpenStreetMap map, lists approximate distances, and opens Google Maps directions.

## Build an APK
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this `LiquorMapIndia` folder in Android Studio.
3. Allow Gradle sync and install any prompted SDK components.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Command line (with Android SDK and Gradle installed): `gradle assembleDebug`.

## Important limitations
- This project is source code; no APK has been compiled in this environment because Android SDK/Gradle are not installed here.
- Shop coverage depends on OpenStreetMap community data. Missing listings do not mean a shop does not exist.
- Product catalogue, live stock, prices, opening hours, and licences are not provided by the map API. The app intentionally does not invent them; verify with the shop and official state sources.
- The app sends approximate location coordinates to the public Overpass API to search map features. Provide location permission only if you want nearby search.
- Google Maps must be installed or a browser available for directions.
- Follow applicable Rajasthan excise rules and the legal purchase age. This app is an informational map, not a seller or purchase service.

## Data attribution
Map data © OpenStreetMap contributors. Tiles are served by OpenStreetMap's tile service; follow its usage policy for production deployments. For a public release, use a compliant tile provider and consider a dedicated Overpass instance or commercial Places provider, plus privacy policy and store-policy review.

## Build from a Samsung Android tablet (cloud build)
This project includes `.github/workflows/build-apk.yml` so the APK can be built on GitHub's hosted Linux runner; Android Studio is not needed on the tablet.

1. In Chrome on the tablet, sign in to GitHub and create a **new public or private repository** named `LiquorMapIndia`.
2. Upload the **contents** of the extracted `LiquorMapIndia` folder to the repository (the files `app`, `build.gradle.kts`, `settings.gradle.kts`, and `.github` should be at the repository root; do not upload the outer ZIP folder as an extra nesting level).
3. Open the repository's **Actions** tab. If GitHub asks, enable Actions.
4. Select **Build LiquorMap India APK** → **Run workflow** → **Run workflow**.
5. Wait for the run to finish with a green check. Open that run, scroll to **Artifacts**, and download `LiquorMapIndia-debug-apk`.
6. Extract the downloaded artifact ZIP; tap `app-debug.apk` to install. If Android blocks it, allow Chrome/My Files to install unknown apps for this installation, then install it.

The APK is a debug build for testing, not a Play Store release. Keep the repository private if you do not want the source code public. Never upload passwords or signing keys.
