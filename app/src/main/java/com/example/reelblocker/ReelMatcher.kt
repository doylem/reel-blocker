package com.example.reelblocker

/**
 * Pure string-matching logic for the accessibility labels Instagram attaches to its UI.
 * Kept separate from [ReelBlockerService] (and free of any `android.*` framework types)
 * so it can run in a plain JVM unit test, without an emulator or Robolectric.
 */
object ReelMatcher {

    // Instagram calls Reels "Clips" internally in a lot of its own code/IDs, so we
    // match on both terms to be resilient to naming drift. Kept only as a fallback in
    // case a future Instagram build re-exposes resource IDs to the accessibility tree
    // (a real device capture confirmed it currently exposes none at all).
    private val REEL_VIEW_ID_KEYWORDS = listOf(
        "clips_tab",
        "clips_viewer",
        "clips_swipe",
        "clips_bottom_tab",
        "reel_viewer",
        "reels_tray",
        "reel_player"
    )

    /**
     * Matches: "Reel by shahin_doors. Double tap to play or pause."
     * Excludes: "Suggested Reel by Daily Brief Global, ..." (an inline Home-feed card,
     * not the immersive player) by requiring the description to *start* with "Reel by".
     */
    fun isReelPlayerDescription(desc: String): Boolean {
        return desc.startsWith("Reel by", ignoreCase = true) &&
            desc.contains("Double tap to play or pause", ignoreCase = true)
    }

    /** Fallback view-ID match, see [REEL_VIEW_ID_KEYWORDS]. */
    fun isReelViewId(viewId: String): Boolean {
        return REEL_VIEW_ID_KEYWORDS.any { viewId.contains(it, ignoreCase = true) }
    }

    /** Identifies Instagram's bottom-nav Home tab, by label or (fallback) view ID. */
    fun isHomeTabNode(description: String?, viewId: String?): Boolean {
        return (description != null && description.equals("Home", ignoreCase = true)) ||
            (viewId != null && viewId.contains("feed_tab", ignoreCase = true))
    }
}
