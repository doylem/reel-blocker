package com.example.reelblocker

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

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
 * find the new label pattern, and update [ReelMatcher.isReelPlayerDescription].
 */
class ReelBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelBlocker"
        private const val DUMP_TAG = "ReelBlockerDump"
        private const val CLIP_TAG = "ReelBlockerClip"
        private const val CLICK_TAG = "ReelBlockerClick"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        private const val COOLDOWN_MS = 1_200L

        // Confirmed via a real adb logcat capture: Instagram does NOT expose
        // viewIdResourceName to the accessibility tree at all (every node came
        // back null). Detection instead keys off the accessibility label
        // Instagram attaches to the immersive Reels player for screen-reader
        // users — see ReelMatcher.isReelPlayerDescription().
        private const val DEBUG_DUMP = false
        private const val DUMP_INTERVAL_MS = 2_000L
    }

    private var lastActionTime = 0L
    private var lastDumpTime = 0L

    // --- Auto clipboard cleaner (unrelated to Reels detection above) ---
    // IG's in-app browser "Copy Link" button injects fbclid onto the URL,
    // which overflows the Stories link-sticker character limit. This watches
    // the clipboard and strips known tracker params the instant something is
    // copied, but only while Instagram is the active app (checked via
    // rootInActiveWindow below) so it never touches clipboard content from
    // other apps.
    private val clipboardManager by lazy {
        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }
    private var isOwnClipboardWrite = false

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardChanged()
    }

    private fun onClipboardChanged() {
        Log.d(CLIP_TAG, "clipboard change detected")

        if (isOwnClipboardWrite) {
            Log.d(CLIP_TAG, "ignoring our own write")
            isOwnClipboardWrite = false
            return
        }

        // Only auto-clean while Instagram is the foreground app.
        val activePackage = rootInActiveWindow?.packageName?.toString()
        Log.d(CLIP_TAG, "active window package = $activePackage")
        if (activePackage != INSTAGRAM_PACKAGE) {
            Log.d(CLIP_TAG, "skipping, not Instagram")
            return
        }

        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Log.d(CLIP_TAG, "clip is empty")
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Log.d(CLIP_TAG, "clip text is blank/null")
            return
        }
        Log.d(CLIP_TAG, "clip text = $text")

        val cleaned = UrlCleaner.cleanText(text)
        if (cleaned == text) {
            Log.d(CLIP_TAG, "nothing to strip")
            return
        }

        Log.d(CLIP_TAG, "cleaned = $cleaned")
        isOwnClipboardWrite = true
        clipboardManager.setPrimaryClip(ClipData.newPlainText("cleaned_url", cleaned))
        Toast.makeText(this, "Removed tracking params from link", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // TEMPORARY diagnostic: log every click from ANY package (not just
        // Instagram) so we can find which window "Copy Link" actually lives
        // in — the Instagram-only capture came up empty, suggesting it's a
        // system share sheet or similar. Remove once found.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            Log.d(
                CLICK_TAG,
                "CLICK pkg=${event.packageName} text=${event.text} desc=${event.contentDescription} " +
                    "class=${event.className} source=${event.source?.viewIdResourceName}"
            )
        }

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
                    "NEAR-MISS candidate desc=\"$desc\" matches=${ReelMatcher.isReelPlayerDescription(desc)} " +
                        "visible=${node.isVisibleToUser}"
                )
            }
            // Instagram keeps the Reels player's fragment/ViewPager page alive in the
            // accessibility tree even after switching back to Home (it's just off-screen),
            // so without the visibility check we'd detect it forever and loop back to
            // Home on every cycle instead of just once.
            if (ReelMatcher.isReelPlayerDescription(desc) && node.isVisibleToUser) return true
        }

        // Kept as a harmless fallback in case a future Instagram build
        // re-exposes resource IDs to the accessibility tree.
        node.viewIdResourceName?.let { id ->
            if (ReelMatcher.isReelViewId(id)) return true
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

        if (ReelMatcher.isHomeTabNode(desc, viewId) && node.isClickable) {
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
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        super.onDestroy()
    }
}
