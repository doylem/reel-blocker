package com.example.reelblocker

import android.net.Uri

/**
 * Shared tracker-param stripping logic, used by both UrlCleanerActivity
 * (manual Share-target flow) and ReelBlockerService's clipboard watcher
 * (automatic Instagram-only flow).
 */
object UrlCleaner {

    private val TRACKER_PARAMS = setOf(
        "fbclid", "gclid", "gbraid", "wbraid", "igshid", "igsh",
        "mc_eid", "mc_cid",
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content"
    )

    private val URL_PATTERN = Regex("""https?://\S+""")

    /** Finds every URL in [text] and strips tracker params from each. */
    fun cleanText(text: String): String =
        URL_PATTERN.replace(text) { match -> cleanUrl(match.value) }

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
