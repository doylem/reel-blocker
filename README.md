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

## If it stops working after an Instagram update

Instagram periodically renames its internal view IDs. If Reels stops getting
blocked:

1. Open Instagram, navigate into Reels.
2. In Android Studio: `View > Tool Windows > Layout Inspector`, attach to the
   Instagram process, and inspect the view tree to find the current resource
   ID for the Reels player / bottom tab (or run
   `adb shell uiautomator dump` and pull the XML).
3. Add the new ID substring to the `REEL_KEYWORDS` list in
   `ReelBlockerService.kt`.
4. Re-run.

This is the same maintenance burden every accessibility-service-based blocker
(free or paid) has — there's no way around Instagram being able to change its
own internal naming.

## Extending it

- To block other things (e.g. Explore grid) alongside Reels, just add more
  keyword substrings to `REEL_KEYWORDS`.
- To block Reels in other apps too (Facebook, YouTube Shorts), duplicate the
  package filter in `accessibility_service_config.xml` and add
  app-specific keywords.
