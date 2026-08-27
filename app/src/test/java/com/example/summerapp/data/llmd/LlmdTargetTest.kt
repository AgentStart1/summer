package com.example.summerapp.data.llmd

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmdTargetTest {
    @Test
    fun preferenceValues_resolveEveryBuildType() {
        LlmdTarget.entries.forEach { target ->
            assertEquals(target, LlmdTarget.fromPreference(target.preferenceValue))
        }
    }

    @Test
    fun missingOrUnknownPreference_defaultsToRelease() {
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference(null))
        assertEquals(LlmdTarget.Release, LlmdTarget.fromPreference("unknown"))
    }
}
