package com.example.reelblocker

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches Instagram's screen (and ONLY Instagram's — see accessibility_service_config.xml)
 * for view IDs associated with the Reels / "Clips" player, and switches back to the
 * Home tab whenever it spots one.
 *
 * NOTE: Instagram obfuscates and periodically changes its internal view IDs. If this
 * stops working after an Instagram update, use Android Studio's Layout Inspector
 * (or `adb shell uiautomator dump`) while Reels is open to find the current IDs and
 * add them to REEL_KEYWORDS below.
 */
class ReelBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelBlocker"
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
    }

    private var lastActionTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != INSTAGRAM_PACKAGE) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()
        if (now - lastActionTime < COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        try {
            if (containsReelsNode(root)) {
                Log.d(TAG, "Reels view detected — redirecting to Home")
                lastActionTime = now
                goHomeOrBack(root)
            }
        } finally {
            root.recycle()
        }
    }

    /** Recursively scans the node tree for a view ID matching any Reels keyword. */
    private fun containsReelsNode(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > 50) return false // guard against pathologically deep trees

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
