package com.nobi.scan.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdatesTest {
    private val hash = "a".repeat(64)

    @Test
    fun acceptsNewerStableAndReleaseCandidateAssets() {
        assertTrue(GitHubUpdates.releaseMatches("v1.0.0", "1.0.0", "NobiScan-v1.0.0-arm64.apk", hash, 10020, 10004))
        assertTrue(GitHubUpdates.releaseMatches("v1.0.0-rc6", "1.0.0-rc6", "NobiScan-v1.0.0-rc6-arm64.apk", hash, 10005, 10004))
    }

    @Test
    fun rejectsAlphaDowngradesAndMismatchedMetadata() {
        assertFalse(GitHubUpdates.releaseMatches("v1.0-alpha22", "1.0-alpha22", "NobiScan-v1.0-alpha22-arm64.apk", hash, 10005, 10004))
        assertFalse(GitHubUpdates.releaseMatches("v1.0.0-rc6", "1.0.0-rc6", "NobiScan-v1.0.0-rc6-arm64.apk", hash, 10004, 10004))
        assertFalse(GitHubUpdates.releaseMatches("v1.0.0-rc6", "1.0.0-rc5", "NobiScan-v1.0.0-rc6-arm64.apk", hash, 10005, 10004))
        assertFalse(GitHubUpdates.releaseMatches("v1.0.0-rc6", "1.0.0-rc6", "../unsigned.apk", hash, 10005, 10004))
        assertFalse(GitHubUpdates.releaseMatches("v1.0.0-rc6", "1.0.0-rc6", "NobiScan-v1.0.0-rc6-arm64.apk", "bad", 10005, 10004))
    }

    @Test
    fun foregroundOffersOnlyVersionsNotAlreadyShown() {
        assertTrue(shouldOfferUpdate(10006, 10005))
        assertFalse(shouldOfferUpdate(10006, 10006))
        assertFalse(shouldOfferUpdate(10005, 10006))
        assertFalse(shouldOfferUpdate(null, 10005))
    }

    @Test
    fun requiresAnExactNonemptySignerSet() {
        assertTrue(signersMatch(listOf("second", "first"), listOf("first", "second")))
        assertFalse(signersMatch(listOf("different"), listOf("first")))
        assertFalse(signersMatch(listOf("first"), listOf("first", "second")))
        assertFalse(signersMatch(emptyList(), emptyList()))
    }
}
