package com.storytellerf.summer.data.llmd

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmdTargetTest {
    @Test
    fun currentPreferencesRoundTrip() {
        LlmdTarget.entries.forEach { target ->
            assertEquals(target, LlmdTarget.fromPreference(target.preferenceValue))
        }
    }

    @Test
    fun missingAndUnknownPreferencesFallBackToRelease() {
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference(null))
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference("unknown"))
    }
}
