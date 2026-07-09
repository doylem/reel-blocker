# AGENTS.md — Reel Blocker project handoff

## What this is
A personal Android app that blocks Instagram Reels while leaving feed/DMs
usable, built as a free/DIY alternative to paid "Reels blocker" apps. It's an
`AccessibilityService` that watches Instagram's screen and, when it detects
the immersive Reels player, taps the phone back to Instagram's Home tab.

The user is tech-savvy, on Android, wants everything as simple/lightweight as
possible, and does NOT want Android Studio installed (disk space). All
building happens via GitHub Actions; the user only needs `git` and `adb`
locally.

## Current status (as of last session)
Detection logic has gone through two iterations:

1. **First attempt (wrong): matched on Android view IDs** (guessed keywords
   like `clips_tab`, `reel_viewer`, etc.). This never worked. We proved why
   via a real `adb logcat` capture: **Instagram does not expose
   `viewIdResourceName` to the accessibility tree at all** — every single
   node in a full-tree dump came back with `id=-`. This ID-based approach
   was fundamentally dead on arrival, not just miscalibrated.

2. **Second attempt (current): match on content-description text.** The same
   capture showed Instagram DOES expose rich, real accessibility labels via
   `contentDescription`, including on the Reels player itself:
   ```
   Reel by shahin_doors. Double tap to play or pause.
   ```
   vs. an in-feed "suggested reel" card in the normal Home feed, which is
   worded differently:
   ```
   Suggested Reel by Daily Brief Global, 3,136 likes, 41 comments, 3 hours ago
   ```
   Current logic in `ReelBlockerService.kt` (`isReelPlayerDescription()`)
   matches strings that `startsWith("Reel by")` AND `contains("Double tap to
   play or pause")`, which correctly excludes the "Suggested Reel by..."
   Home-feed wording so normal feed scrolling isn't disrupted.

**Problem: this second version has NOT yet been confirmed working.** The
user reported "Reels still played" after installing a build with this logic.
The service IS running (`"Reel Blocker service connected"` appears in
logcat), but no `"Reels player detected"` log line appeared either — meaning
detection just isn't matching, for reasons not yet diagnosed.

**Last action taken:** re-enabled `DEBUG_DUMP = true` and added a
"near-miss" diagnostic — logs any node whose `contentDescription` merely
contains the substring "reel" (case-insensitive), along with whether it
matched `isReelPlayerDescription()`. This build has been pushed but **we do
not yet have the resulting log output**. This is the very next step:
capture `adb logcat -s ReelBlocker:D ReelBlockerDump:D` while opening Reels,
look for `NEAR-MISS candidate` lines, and see why the match is failing —
likely candidates: extra/different whitespace, a slightly different phrase
Instagram is using now, the match logic only checking `contentDescription`
and missing some other node property, or the relevant node simply isn't
being traversed for some reason (e.g. it's outside `rootInActiveWindow`,
though the original capture proved that same tree traversal *did* reach that
text, so this is less likely — the code between the two captures shouldn't
have changed the traversal itself, only the matching function).

## Repo / build setup

- **Build:** GitHub Actions (`.github/workflows/build.yml`), triggered on
  push to `main`/`master`. Produces artifact `ReelBlocker-debug-apk`
  containing `app-debug.apk`.
- **No Android Studio needed** — GitHub's hosted runners have the Android
  SDK preinstalled; the workflow just runs `gradle assembleDebug`.
- **Signing:** originally tried caching `~/.android/debug.keystore` across
  CI runs via `actions/cache` — this was unreliable (a rebuild after the
  cache should have been warm still produced a signature mismatch on
  install). **Fixed by committing an actual fixed debug keystore into the
  repo** at `keystore/debug.keystore`, referenced directly from
  `app/build.gradle`'s `signingConfigs.debug` block (alias
  `androiddebugkey`, password `android` — standard Android debug key
  convention). This is intentional and safe to commit; debug keys aren't
  secret. Every build from GitHub Actions or a local machine is now signed
  identically, so `adb install -r` should always update in place.
- **User's phone adb serial:** `R5CX92M16GR` (there's also a stray
  `emulator-5562 offline` entry in `adb devices` on this machine — ignore it,
  it isn't the phone). Because there's more than one device, adb commands
  need `-s R5CX92M16GR` right after `adb` (not mixed into subcommand flags),
  e.g.:
  ```
  adb -s R5CX92M16GR install -r app-debug.apk
  adb -s R5CX92M16GR uninstall com.example.reelblocker
  adb -s R5CX92M16GR logcat -s ReelBlocker:D ReelBlockerDump:D
  ```
- **Sideloading gotcha:** installing the APK by tapping it from Dropbox/Files
  hits Android's "restricted settings" block for sideloaded apps (blocks the
  Accessibility permission toggle). Installing via `adb install` bypasses
  this entirely — adb-installed apps aren't treated as "unknown source" for
  this purpose. Prefer `adb install` over manual sideloading going forward.
- **Package name:** `com.example.reelblocker`. Service class:
  `.ReelBlockerService`. After any fresh install (not update), the user must
  re-enable it at Settings > Accessibility > Installed apps > Reel Blocker.

## Key files

- `app/src/main/java/com/example/reelblocker/ReelBlockerService.kt` — the
  core accessibility service and all detection logic. This is almost
  certainly where the next fix needs to happen.
- `app/src/main/java/com/example/reelblocker/MainActivity.kt` — trivial
  status screen + button to open Accessibility settings.
- `app/src/main/res/xml/accessibility_service_config.xml` — service config,
  scoped to `com.instagram.android` only.
- `.github/workflows/build.yml` — CI build.
- `keystore/debug.keystore` — fixed signing key, see above.
- `README.md` — user-facing setup/troubleshooting doc, kept in sync with
  whatever the current detection approach is.

## Immediate next step for whoever picks this up

1. Get the `NEAR-MISS candidate` log output from the user (or run it
   yourself if you have adb access to the same phone) to see the *actual*
   live content-description string(s) on the Reels player right now.
2. Update `isReelPlayerDescription()` to match reality.
3. Set `DEBUG_DUMP = false` once confirmed working (currently `true` — logs
   a full tree dump every ~2s while on Instagram, which is noisy/costs a
   little battery, fine for debugging but shouldn't ship long-term).
4. Remove or keep the near-miss diagnostic log line as a permanent low-cost
   canary — up to you; it only logs when "reel" appears in a description, so
   it's cheap.
5. Consider whether the fallback `performGlobalAction(GLOBAL_ACTION_BACK)`
   (used when the Home tab node can't be found) is firing correctly, once
   detection itself is confirmed — it hasn't been meaningfully tested yet
   since detection never triggered it in a live test.

## Things NOT to re-litigate (already settled, don't redo this work)

- Android Studio is deliberately avoided; keep using GitHub Actions + adb.
- View-ID-based detection is confirmed dead; don't suggest going back to it.
- Keystore caching via `actions/cache` is confirmed unreliable; the fixed
  committed keystore is the deliberate replacement — don't revert to caching.
- Sideloading via Dropbox/Files works but requires jumping through
  "restricted settings"; `adb install` is strictly better for this user's
  workflow and should be the default recommendation.
