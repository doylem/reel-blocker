# AGENTS.md — Reel Blocker: URL Cleaner share target

## Context

This app already runs a working AccessibilityService that blocks Instagram
Reels while leaving the rest of the IG app usable. This handover adds a
second, unrelated feature to the same APK: a share-target Activity that
strips tracking params (`fbclid` and friends) from URLs before they get
pasted somewhere like an Instagram Story link sticker. IG's share/copy
flow injects `fbclid` onto links, which was overflowing the character
limit on the Stories link sticker.

This feature is fully independent of the AccessibilityService. Do not
touch or refactor the existing Reel-blocking detection logic as part of
this task.

## Build/install conventions for this project

- No Android Studio. Build via GitHub Actions CI.
- Install with `adb install -r` to device serial `R5CX92M16GR`.
- Confirm the existing CI workflow still passes with the new file added
  before considering this done.

## Task

Add a new Activity, `UrlCleanerActivity`, registered as a share target for
plain text (`ACTION_SEND`, mime type `text/plain`). When the user shares a
link (or a string containing a link) to it:

1. Find the URL substring inside the shared text via regex.
2. Strip known tracking query params from it (see `TRACKER_PARAMS` below,
   extend as needed: `fbclid`, `gclid`, `gbraid`, `wbraid`, `igshid`,
   `igsh`, `mc_eid`, `mc_cid`, `utm_source`, `utm_medium`, `utm_campaign`,
   `utm_term`, `utm_content`).
3. Copy the cleaned text back to the clipboard.
4. Show a short Toast confirming it worked.
5. Finish immediately (`Theme.NoDisplay`) — no visible screen, no
   perceptible time spent "in" the app.

## Before writing code, check

- The actual package name to use (match whatever the existing Activity/
  Service classes in this project declare — don't guess).
- Where `AndroidManifest.xml` already declares the AccessibilityService,
  and add the new `<activity>` block as a sibling entry, not nested
  inside anything else.
- Whether the app's launcher label ("Reel Blocker") is what should show
  up in the Android share sheet for this activity, or whether a separate
  `android:label="Clean link"` (already included below) reads better in
  the share picker.

## Implementation

`UrlCleanerActivity.kt` (adjust the package line to match the project):

```kotlin
package com.yourpackage.reelblocker // TODO: match project's actual package

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

private val TRACKER_PARAMS = setOf(
    "fbclid", "gclid", "gbraid", "wbraid", "igshid", "igsh",
    "mc_eid", "mc_cid",
    "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
)

private val URL_PATTERN = Regex("""https?://\S+""")

class UrlCleanerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }

        val cleaned = URL_PATTERN.replace(sharedText) { match ->
            cleanUrl(match.value)
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cleaned_url", cleaned))
        Toast.makeText(this, "Cleaned + copied", Toast.LENGTH_SHORT).show()

        finish()
    }

    private fun cleanUrl(rawUrl: String): String {
        // the regex above can grab trailing punctuation (a period ending a
        // sentence, a closing bracket, etc) that isn't part of the URL
        val trailing = rawUrl.takeLastWhile { it in ".,)!?" }
        val trimmedUrl = rawUrl.dropLast(trailing.length)

        val uri = try {
            Uri.parse(trimmedUrl)
        } catch (e: Exception) {
            return rawUrl
        }

        if (uri.query == null) return rawUrl

        val keepParams = uri.queryParameterNames.filterNot {
            it.lowercase() in TRACKER_PARAMS
        }

        val builder = uri.buildUpon().clearQuery()
        for (key in keepParams) {
            for (value in uri.getQueryParameters(key)) {
                builder.appendQueryParameter(key, value)
            }
        }

        return builder.build().toString() + trailing
    }
}
```

Add to `AndroidManifest.xml`, as a sibling of the existing
`<activity>`/`<service>` entries inside `<application>`:

```xml
<activity
    android:name=".UrlCleanerActivity"
    android:exported="true"
    android:theme="@android:style/Theme.NoDisplay"
    android:excludeFromRecents="true"
    android:label="Clean link">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

## Testing

1. Build via the existing GitHub Actions workflow.
2. `adb install -r` the resulting APK to `R5CX92M16GR`.
3. Copy or share a URL with a `fbclid=` (or `utm_*`) param on it from
   another app.
4. Confirm "Clean link" (or whatever label ends up used) shows in the
   share sheet, tapping it shows the "Cleaned + copied" toast and returns
   immediately with no visible screen.
5. Paste from clipboard somewhere and confirm the tracker param is gone
   and the rest of the URL/query string is untouched.
6. Confirm the existing Reel-blocking AccessibilityService still behaves
   as before — this change shouldn't interact with it at all, but worth
   a quick sanity check post-build.

## Open questions for whoever picks this up

- Final choice of share-sheet label if "Clean link" doesn't fit.
- Whether to extend `TRACKER_PARAMS` further once real-world links get
  tested against it.
