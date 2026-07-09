package com.example.reelblocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelMatcherTest {

    @Test
    fun `matches the real immersive player label captured from a device`() {
        assertTrue(
            ReelMatcher.isReelPlayerDescription("Reel by shahin_doors. Double tap to play or pause.")
        )
    }

    @Test
    fun `matches regardless of case`() {
        assertTrue(
            ReelMatcher.isReelPlayerDescription("reel by someuser. double tap to play or pause.")
        )
    }

    @Test
    fun `does not match an inline Suggested Reel card in the Home feed`() {
        assertFalse(
            ReelMatcher.isReelPlayerDescription(
                "Suggested Reel by Daily Brief Global, 3,136 likes, 41 comments, 3 hours ago"
            )
        )
    }

    @Test
    fun `does not match the reels tray container`() {
        assertFalse(ReelMatcher.isReelPlayerDescription("reels tray container"))
    }

    @Test
    fun `does not match unrelated descriptions containing reel`() {
        assertFalse(ReelMatcher.isReelPlayerDescription("Reel by Zankee Gulati at row 3, column 2"))
    }

    @Test
    fun `does not match a description missing the double-tap suffix`() {
        assertFalse(ReelMatcher.isReelPlayerDescription("Reel by someuser."))
    }

    @Test
    fun `recognizes reel view id keywords case-insensitively`() {
        assertTrue(ReelMatcher.isReelViewId("com.instagram.android:id/clips_viewer_container"))
        assertTrue(ReelMatcher.isReelViewId("REELS_TRAY_root"))
    }

    @Test
    fun `does not recognize unrelated view ids`() {
        assertFalse(ReelMatcher.isReelViewId("com.instagram.android:id/feed_tab"))
    }

    @Test
    fun `recognizes the Home tab by its content description`() {
        assertTrue(ReelMatcher.isHomeTabNode("Home", null))
        assertTrue(ReelMatcher.isHomeTabNode("home", null))
    }

    @Test
    fun `recognizes the Home tab by its fallback view id`() {
        assertTrue(ReelMatcher.isHomeTabNode(null, "com.instagram.android:id/feed_tab"))
    }

    @Test
    fun `does not recognize other bottom nav tabs as Home`() {
        assertFalse(ReelMatcher.isHomeTabNode("Reels", null))
        assertFalse(ReelMatcher.isHomeTabNode("Profile", "com.instagram.android:id/profile_tab"))
        assertFalse(ReelMatcher.isHomeTabNode(null, null))
    }
}
