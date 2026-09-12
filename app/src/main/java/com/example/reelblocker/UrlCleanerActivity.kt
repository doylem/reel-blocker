package com.example.reelblocker

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
