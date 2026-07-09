# AGENTS.md — Reel Blocker project guide

## What this is

A tiny Android app that blocks Instagram Reels while leaving feed/DMs
usable. It's an `AccessibilityService`, scoped to `com.instagram.android`
only, that watches Instagram's on-screen accessibility tree and, when it
detects the immersive Reels player, taps the phone back to Instagram's Home
tab (falling back to the system Back action if it can't find the Home tab).

It's built to require zero local Android tooling: all builds run on GitHub
Actions, and the only local tools needed are `git` and `adb`. See
[README.md](README.md) for end-user install instructions — this file is for
whoever is extending or maintaining the code itself.

## Architecture

### Detection strategy

Instagram does **not** expose `viewIdResourceName` to the accessibility tree
at all (confirmed via a real `adb logcat` full-tree capture — every node came
back `id=-`). So detection keys off `contentDescription` instead: the label
Instagram attaches to the immersive Reels player for screen-reader users,
e.g.:

```
Reel by shahin_doors. Double tap to play or pause.
```

This is matched by `ReelMatcher.isReelPlayerDescription()` — `startsWith("Reel
by")` AND `contains("Double tap to play or pause")`. The `startsWith` check
is deliberate: Instagram's Home-feed inline "suggested reel" cards use a
different, non-immersive wording (`"Suggested Reel by someuser, 41 comments,
..."`), which must **not** trigger a redirect, or ordinary feed scrolling
would be constantly interrupted.

### The off-screen-node trap (already hit and fixed once — don't reintroduce)

Content-description matching alone isn't sufficient, because Instagram keeps
the Reels tab's `ViewPager` page (and its accessibility nodes) instantiated
in the tree even while you're on Home — it's just positioned off-screen, not
destroyed. A pure tree-wide description match will keep finding that stale
node forever and loop the redirect on every polling cycle (this was observed
live: the app snapped back to Home once, then kept re-triggering every
~1.2–2s indefinitely). The fix, in `ReelBlockerService.containsReelsNode()`,
is to additionally require `node.isVisibleToUser` before treating a match as
real. Any future detection logic must preserve this visibility gate.

### Code layout

- `ReelMatcher.kt` — pure string-matching logic (no `android.*` imports),
  covering: the immersive Reels player label, a view-ID fallback (kept in
  case Instagram ever re-exposes resource IDs), and the bottom-nav Home tab
  label. Deliberately framework-free so it's unit-testable on the JVM without
  an emulator or Robolectric.
- `ReelBlockerService.kt` — the `AccessibilityService`. Walks the
  accessibility tree (`containsReelsNode`, `findHomeTabNode`), calls into
  `ReelMatcher` for the actual string matching, and performs the redirect
  (`goHomeOrBack`). Also has an optional full-tree logging mode
  (`DEBUG_DUMP`) and a permanent low-cost "near-miss" log line (any node
  whose description merely contains "reel", cheap since it's a simple
  substring check) for diagnosing future Instagram wording changes.
- `MainActivity.kt` — trivial status screen (shows whether the accessibility
  service is currently enabled) + a button that opens Accessibility settings
  directly.
- `app/src/main/res/xml/accessibility_service_config.xml` — service config;
  `android:packageNames="com.instagram.android"` is what scopes the service
  to Instagram only. `android:canRetrieveWindowContent="true"` is required
  for tree inspection.
- `app/src/test/.../ReelMatcherTest.kt` — JUnit unit tests for `ReelMatcher`.
  Runs in CI via `gradle testDebugUnitTest` before every build.

## Build / CI / release pipeline

- **Build:** GitHub Actions (`.github/workflows/build.yml`), triggered on
  push to `main`/`master`, or manually via `workflow_dispatch`. Runs
  `gradle testDebugUnitTest` then `gradle assembleDebug` on GitHub's hosted
  runners (Android SDK preinstalled — no wrapper or local SDK needed). The
  built APK is uploaded as the `ReelBlocker-debug-apk` workflow artifact.
- **Releases:** built APKs are attached to tagged GitHub Releases (see the
  [Releases page](../../releases)) rather than committed into the repo —
  committing binaries directly bloats git history and was tried and reverted
  once already; don't reintroduce that pattern.
- **Signing:** the repo commits a fixed `keystore/debug.keystore` (standard
  Android debug alias/password: `androiddebugkey` / `android`), referenced
  from `app/build.gradle`'s `signingConfigs.debug`. This is intentional and
  safe — debug keys aren't secret, Android Studio generates an equivalent one
  locally on every machine. The point is that every build (CI or local) is
  signed identically, so `adb install -r` always updates in place instead of
  hitting `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. (Switching *to* this fixed
  keystore from some other signature requires one uninstall/reinstall; after
  that, updates apply cleanly.)
- **Package name:** `com.example.reelblocker`. Service class:
  `.ReelBlockerService`. A **fresh install** (not an update) resets the
  Accessibility toggle — re-enable at Settings > Accessibility > Installed
  apps > Reel Blocker. Also, installing via `adb install` (rather than
  tapping the APK from Files/Dropbox/etc.) skips Android's "restricted
  settings" block on newly sideloaded apps, so the toggle works immediately.

## Testing

Unit tests cover `ReelMatcher` only — the pure string-matching logic. The
accessibility-tree-walking code in `ReelBlockerService` (`containsReelsNode`,
`findHomeTabNode`, `dumpTree`) depends on `android.view.accessibility.*`
framework types and is not unit tested; it's exercised by manual on-device
testing instead (see README's troubleshooting section for the `DEBUG_DUMP`/
`adb logcat` workflow). If you refactor tree-walking logic, prefer extracting
any new pure decision logic into `ReelMatcher` so it stays testable, rather
than growing untested logic inside the tree walk.

## Extending

- **Block other Instagram surfaces** (e.g. the Explore grid): capture its
  accessibility label via `DEBUG_DUMP`, add a matcher function to
  `ReelMatcher`, cover it with a test, then call it from
  `containsReelsNode()`.
- **Block Reels-equivalent content in other apps** (Facebook, YouTube
  Shorts): add the target package to
  `accessibility_service_config.xml`'s `android:packageNames` (colon-
  separated list) and `INSTAGRAM_PACKAGE`-style checks in
  `ReelBlockerService`, then add app-specific matchers to `ReelMatcher` (the
  label wording will differ per app).
- Whatever you add, preserve the `isVisibleToUser` gate (see "The
  off-screen-node trap" above) — it's easy to reintroduce the redirect loop
  by matching on tree presence alone.

## Known limitations / maintenance burden

- Instagram can change the Reels player's accessibility label wording at any
  time, silently breaking detection. There's no way around this — it's the
  same burden every accessibility-service-based blocker (free or paid) has.
  The `NEAR-MISS` log line exists specifically to make re-diagnosing this
  fast: it fires on any node whose description merely contains "reel",
  whether or not it currently matches, so a fresh device capture will show
  the new wording immediately.
- No automated on-device/instrumentation testing exists (would require an
  emulator or physical device in CI); all tree-walking behavior is verified
  manually. If this becomes painful, consider Robolectric for
  `AccessibilityNodeInfo` shadows rather than a full instrumentation suite.
