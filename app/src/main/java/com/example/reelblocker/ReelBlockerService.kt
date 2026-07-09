package com.example.reelblocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches Instagram's screen (and ONLY Instagram's — see accessibility_service_config.xml)
 * for the accessibility label Instagram attaches to the immersive Reels player
 * (e.g. "Reel by someuser. Double tap to play or pause."), and switches back to
 * the Home tab whenever it spots one.
 *
 * NOTE: this was confirmed against a real device capture — Instagram does not
 * expose resource IDs (viewIdResourceName) to the accessibility tree at all, so
 * detection deliberately keys off content-description text instead. If this
 * stops working after an Instagram update, capture a fresh `adb logcat -s
 * ReelBlockerDump:D` dump (flip DEBUG_DUMP to true below) while Reels is open,
 * find the new label pattern, and update isReelPlayerDescription() below.
 */
class ReelBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelBlocker"
        private const val DUMP_TAG = "ReelBlockerDump"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        // Instagram calls Reels "Clips" internally in a lot of its own code/IDs,
        // so we match on both terms to be resilient to naming drift.
        private val REEL_KEYWORDS = listOf(
            "clips_tab",
            "clips_viewer",
            "clips_swipe",
            "clips_bottom_tab",
            "reel_viewer",
            "reels_tray",
            "reel_player"
        )

        private const val COOLDOWN_MS = 1_200L

        // Confirmed via a real adb logcat capture: Instagram does NOT expose
        // viewIdResourceName to the accessibility tree at all (every node came
        // back null). Detection instead keys off the accessibility label
        // Instagram attaches to the immersive Reels player for screen-reader
        // users, e.g. "Reel by shahin_doors. Double tap to play or pause."
        // This deliberately excludes "Suggested Reel by ..." cards, which is
        // the wording used for an inline reel card in the normal Home feed —
        // we don't want to kick the user out of their feed for those.
        private const val DEBUG_DUMP = true
        private const val DUMP_INTERVAL_MS = 2_000L
    }

    private var lastActionTime = 0L
    private var lastDumpTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != INSTAGRAM_PACKAGE) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val root = rootInActiveWindow ?: return
        try {
            if (DEBUG_DUMP) {
                val now = System.currentTimeMillis()
                if (now - lastDumpTime > DUMP_INTERVAL_MS) {
                    lastDumpTime = now
                    Log.d(DUMP_TAG, "===== DUMP START =====")
                    dumpTree(root)
                    Log.d(DUMP_TAG, "===== DUMP END =====")
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastActionTime < COOLDOWN_MS) return

            if (containsReelsNode(root)) {
                Log.d(TAG, "Reels player detected — redirecting to Home")
                lastActionTime = now
                goHomeOrBack(root)
            }
        } finally {
            root.recycle()
        }
    }

    /** Logs every node's class name, view ID, and content description/text. */
    private fun dumpTree(node: AccessibilityNodeInfo, depth: Int = 0) {
        if (depth > 60) return
        val indent = "  ".repeat(depth)
        val id = node.viewIdResourceName ?: "-"
        val cls = node.className ?: "-"
        val desc = node.contentDescription ?: "-"
        val text = node.text ?: "-"
        Log.d(DUMP_TAG, "$indent[$depth] class=$cls id=$id desc=$desc text=$text")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpTree(child, depth + 1)
            child.recycle()
        }
    }

    /** Recursively scans the node tree for the immersive Reels player's a11y label. */
    private fun containsReelsNode(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 50) return false // guard against pathologically deep trees

        node.contentDescription?.toString()?.let { desc ->
            if (desc.contains("reel", ignoreCase = true)) {
                Log.d(
                    DUMP_TAG,
                    "NEAR-MISS candidate desc=\"$desc\" matches=${isReelPlayerDescription(desc)} " +
                        "visible=${node.isVisibleToUser}"
                )
            }
            // Instagram keeps the Reels player's fragment/ViewPager page alive in the
            // accessibility tree even after switching back to Home (it's just off-screen),
            // so without the visibility check we'd detect it forever and loop back to
            // Home on every cycle instead of just once.
            if (isReelPlayerDescription(desc) && node.isVisibleToUser) return true
        }

        // Kept as a harmless fallback in case a future Instagram build
        // re-exposes resource IDs to the accessibility tree.
        node.viewIdResourceName?.let { id ->
            if (REEL_KEYWORDS.any { id.contains(it, ignoreCase = true) }) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = containsReelsNode(child, depth + 1)
            child.recycle()
            if (hit) return true
        }
        return false
    }

    private fun isReelPlayerDescription(desc: String): Boolean {
        // Matches: "Reel by shahin_doors. Double tap to play or pause."
        // Excludes: "Suggested Reel by Daily Brief Global, ..." (Home feed card)
        return desc.startsWith("Reel by", ignoreCase = true) &&
            desc.contains("Double tap to play or pause", ignoreCase = true)
    }

    /** Prefer tapping the Home tab (keeps Instagram open); fall back to system back. */
    private fun goHomeOrBack(root: AccessibilityNodeInfo) {
        val homeNode = findHomeTabNode(root)
        if (homeNode != null) {
            homeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            homeNode.recycle()
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun findHomeTabNode(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 50) return null

        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName
        val looksLikeHome = (desc != null && desc.equals("Home", ignoreCase = true)) ||
            (viewId != null && viewId.contains("feed_tab", ignoreCase = true))

        if (looksLikeHome && node.isClickable) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findHomeTabNode(child, depth + 1)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Reel Blocker service connected")
    }
}
