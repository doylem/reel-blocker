# Reel Blocker

A tiny, single-purpose Android app: it watches Instagram's screen (nothing else)
and switches you to the Home tab whenever it detects the Reels player. No
subscription, no analytics, no network permission — the app doesn't even
request internet access.

## How it works

Android's `AccessibilityService` API lets an app (with your explicit permission)
inspect the on-screen view hierarchy of other apps, scoped here to
`com.instagram.android` only (see `accessibility_service_config.xml`). When it
finds a view ID Instagram uses internally for Reels (Instagram calls Reels
"Clips" in a lot of its own code), it taps the Home tab for you.

## About the debug keystore

This project includes `keystore/debug.keystore`, a fixed Android debug signing
key committed directly into the repo. This is intentional and safe — debug
keys are meant to be shared/local (unlike release keys, which must stay
secret); Android Studio itself auto-generates one of these on every machine
using the same well-known alias/password. Committing a fixed one here just
means every build — whether from GitHub Actions or your own machine — is
signed identically, so `adb install -r` always updates in place instead of
occasionally failing with a signature mismatch.

You'll need to uninstall + reinstall once when first switching to this
keystore (since it differs from whatever was signing the app before), but
every build after that should install right over the previous one.

## Setup — Option A: build it with GitHub Actions (no local install, no disk space)

If you don't want to install Android Studio or the Android SDK locally, this
project includes `.github/workflows/build.yml`, which builds the APK on
GitHub's free build servers (they come with the Android SDK preinstalled).

1. Create a new **private** repo on github.com (name doesn't matter).
2. Push this folder to it:
   ```
   cd ReelBlocker
   git init
   git add .
   git commit -m "Reel Blocker"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo-name>.git
   git push -u origin main
   ```
3. On github.com, open your repo's **Actions** tab. A "Build APK" run should
   already be in progress (triggered by the push). Wait for the green check
   (~2-3 minutes).
4. Click into the finished run, scroll to **Artifacts**, and download
   `ReelBlocker-debug-apk` — it's a zip containing `app-debug.apk`.
5. Transfer that APK to your phone (email it to yourself, save it in Google
   Drive, or just download it directly from github.com in your phone's
   browser inside the Actions run page).
6. On your phone, tap the APK file to install it. Android will ask you to
   allow installs from whatever app you opened it with (Files, Chrome,
   Gmail, etc.) — approve that once.
7. Continue to step 5 below (enabling the accessibility permission).

This uses zero disk space on your machine beyond the project folder itself
(a few KB of text files) — no SDK, no emulator, no IDE.

## Setup — Option B: Android Studio (if you have the space / want a debugger)

1. Install [Android Studio](https://developer.android.com/studio) if you don't
   have it.
2. Open this folder as a project (`File > Open`, pick the `ReelBlocker` folder).
   Let Gradle sync — first sync will download the Android Gradle Plugin and
   Kotlin, so it needs internet access once.
3. Connect your phone via USB with USB debugging enabled (Settings > About
   phone > tap "Build number" 7 times > Developer options > USB debugging),
   or use Android Studio's wireless debugging.
4. Click Run ▶ to install it on your phone.

## Enable the service (both options)

5. Open the "Reel Blocker" app, tap **Open Accessibility Settings**, find
   **Reel Blocker** in the list, and turn it on. Android will show a warning
   dialog about accessibility services being powerful — that's standard for
   any app using this API, not a red flag specific to this one.
6. Open Instagram and try tapping into Reels — it should bounce you back to
   Home almost instantly.

## If Reels isn't being blocked (or stops working after an Instagram update)

Detection is based on the accessibility label Instagram attaches to the
immersive Reels player (confirmed via a real device capture — Instagram does
NOT expose view/resource IDs to the accessibility tree, so matching on IDs
never worked; this is why the app now matches on content-description text
instead, e.g. `"Reel by someuser. Double tap to play or pause."`).

If Instagram changes this label in a future update and blocking stops working:

1. **Install `adb` only — not Android Studio.** Download "SDK Platform-Tools"
   for your OS from Google directly (search "android platform tools
   download"); it's a ~15MB zip, just a handful of command-line binaries.
   Unzip it anywhere.
2. Enable USB debugging on your phone: Settings > About phone > tap "Build
   number" 7 times > Developer options > USB debugging.
3. Plug your phone in via USB. On your phone, approve the "Allow USB
   debugging?" prompt.
4. In a terminal, `cd` into the platform-tools folder, then run:
   ```
   adb devices
   ```
   (If more than one device/emulator shows up, pin the command to your phone
   with `-s <serial>` right after `adb`, e.g. `adb -s R5CX92M16GR devices`.)
5. In `ReelBlockerService.kt`, set `DEBUG_DUMP = true`, rebuild via the GitHub
   Actions workflow, and reinstall the APK (`adb install -r app-debug.apk` —
   installing via adb skips Android's "restricted settings" block entirely,
   so no extra steps needed).
6. Run:
   ```
   adb logcat -s ReelBlockerDump:D
   ```
7. Open Instagram and tap into Reels. You'll see a stream of lines like:
   ```
   [7] class=android.view.ViewGroup id=- desc=Reel by someuser. Double tap to play or pause. text=-
   ```
8. Find the current wording of that label and update `isReelPlayerDescription()`
   in `ReelBlockerService.kt` to match it.
9. Once confirmed working again, set `DEBUG_DUMP = false` to stop the log
   spam and save a little battery.

This is the same maintenance burden every accessibility-service-based blocker
(free or paid) has — there's no way around Instagram being able to change its
own internal naming.

## Extending it

- To block other things (e.g. Explore grid) alongside Reels, just add more
  keyword substrings to `REEL_KEYWORDS`.
- To block Reels in other apps too (Facebook, YouTube Shorts), duplicate the
  package filter in `accessibility_service_config.xml` and add
  app-specific keywords.
