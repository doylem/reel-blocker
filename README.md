<div align="center">

# 📵 Reel Blocker

**A free, tiny Android app that blocks Instagram Reels — nothing else.**

[![Build APK](https://github.com/doylem/reel-blocker/actions/workflows/build.yml/badge.svg)](https://github.com/doylem/reel-blocker/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/doylem/reel-blocker)](https://github.com/doylem/reel-blocker/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/tools/releases/platforms)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

</div>

Feed, Stories, DMs, and posting all work exactly as normal. The moment the
Reels player opens, you're bounced straight back to your Home tab. No
subscription, no account, no ads, no analytics, and no network permission —
the app doesn't even request internet access.

Built as a free/DIY alternative to paid "Reels blocker" apps, for anyone who
wants their feed and DMs but not the infinite-scroll video trap.

## How it works

Android's `AccessibilityService` API lets an app — with your explicit,
revocable permission — inspect the on-screen content of other apps. This one
is scoped to `com.instagram.android` only (see
`accessibility_service_config.xml`); it cannot see or act on any other app.

It watches for the accessibility label Instagram itself attaches to the
immersive Reels player (the text a screen reader would announce), e.g.:

```
Reel by someuser. Double tap to play or pause.
```

The moment that label appears, the service taps your Home tab for you. It
deliberately does *not* match the wording Instagram uses for an inline
"Suggested Reel" card in your normal Home feed (`"Suggested Reel by
someuser, 41 comments, ..."`), so ordinary feed scrolling is untouched.

## Install

### Option A — prebuilt APK (fastest)

Check the [Releases](../../releases) page for a prebuilt `app-debug.apk`.
Download it to your phone and skip to [Enable the service](#enable-the-service)
below.

### Option B — build it yourself with GitHub Actions (no Android Studio needed)

This repo builds itself on GitHub's free hosted runners (they come with the
Android SDK preinstalled) via `.github/workflows/build.yml` — no local
install, no disk space used beyond this folder.

1. Fork or clone this repo, and push it to your own GitHub repo.
2. Open your repo's **Actions** tab. A "Build APK" run should already be in
   progress (triggered by the push). Wait for the green check (~2-3 minutes).
3. Open the finished run, scroll to **Artifacts**, and download
   `ReelBlocker-debug-apk` — it's a zip containing `app-debug.apk`.
4. Get that APK onto your phone (see below) and install it.

**Installing the APK:** the easiest way is `adb install -r app-debug.apk`
(see [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)
if you don't have `adb`) — this also sidesteps Android's "restricted
settings" block on newly sideloaded apps, so the Accessibility toggle in the
next step works immediately. If you instead tap the APK directly from Files/
Dropbox/etc. to install it, Android may block the Accessibility toggle for a
few minutes as an anti-malware measure; just wait it out or reinstall via
`adb`.

### Option C — Android Studio (if you already have it / want a debugger)

1. Install [Android Studio](https://developer.android.com/studio).
2. Open this folder as a project and let Gradle sync.
3. Connect your phone via USB with USB debugging enabled (Settings > About
   phone > tap "Build number" 7 times > Developer options > USB debugging).
4. Click Run ▶.

## Enable the service

1. Open the **Reel Blocker** app, tap **Open Accessibility Settings**, find
   **Reel Blocker** in the list, and turn it on. Android shows a standard
   warning about accessibility services being powerful — that's boilerplate
   for any app using this API, not a red flag specific to this one.
2. Open Instagram and tap into Reels — it should bounce you back to Home
   almost instantly.

## About the debug keystore

This project includes `keystore/debug.keystore`, a fixed Android debug
signing key committed directly into the repo. This is intentional and safe —
debug keys are meant to be shared (unlike release keys, which must stay
secret); Android Studio itself auto-generates one of these on every machine
using the same well-known alias/password. Committing a fixed one here means
every build — GitHub Actions or your own machine — is signed identically, so
`adb install -r` always updates in place instead of occasionally failing with
a signature mismatch. (If you're switching from some other build of this app,
you'll need to uninstall once first, since the old signature won't match.)

## If Reels isn't being blocked (or stops working after an Instagram update)

Instagram can change this label's wording at any time — this is the same
maintenance burden every accessibility-service-based blocker (free or paid)
has, there's no way around it.

1. Install `adb` if you don't have it (see above).
2. In `ReelBlockerService.kt`, set `DEBUG_DUMP = true`, rebuild, and reinstall
   (`adb install -r app-debug.apk`).
3. Capture logs while opening Reels:
   ```
   adb logcat -s ReelBlocker:D ReelBlockerDump:D
   ```
   (If more than one device shows up in `adb devices`, pin the command to
   your phone with `-s <serial>` right after `adb`.)
4. Look for `NEAR-MISS candidate desc="..."` lines — these fire on any node
   whose description merely contains "reel", so they'll show you Instagram's
   current exact wording even if it no longer matches.
5. Update `isReelPlayerDescription()` in `ReelBlockerService.kt` to match the
   new wording, rebuild, reinstall.
6. Set `DEBUG_DUMP = false` again once confirmed working, to stop the log
   spam and save a little battery.

## Extending it

- To block Reels in other apps too (Facebook, YouTube Shorts), duplicate the
  package filter in `accessibility_service_config.xml` and add an
  app-specific description matcher alongside `isReelPlayerDescription()`.
- To block other Instagram surfaces (e.g. the Explore grid), find their
  accessibility label the same way (via `DEBUG_DUMP`) and add a matcher.

## Testing

The label-matching logic lives in a plain Kotlin object, `ReelMatcher`
(`app/src/main/java/com/example/reelblocker/ReelMatcher.kt`), kept free of
any Android framework types specifically so it's unit-testable without an
emulator. Tests live in `app/src/test/.../ReelMatcherTest.kt` and cover the
real captured label strings, case-insensitivity, and the "Suggested Reel"
Home-feed wording that must *not* match. They run automatically in CI (`gradle testDebugUnitTest`) before every build;
run them locally the same way if you have Gradle and the Android SDK
installed (this project doesn't commit a Gradle wrapper, to keep the repo
free of Android Studio requirements — see [Setup](#install) above).

If you add a new matcher (e.g. for a different app or a different Instagram
surface), add it to `ReelMatcher` and cover it with a test the same way.

## Contributing

Issues and PRs welcome — especially reports of Instagram wording changes
that break detection (include the `NEAR-MISS` log line from step 4 above).
