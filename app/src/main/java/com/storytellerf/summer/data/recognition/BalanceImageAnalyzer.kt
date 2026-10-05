package com.storytellerf.summer.data.recognition

import com.storytellerf.summer.data.llmd.LlmdTarget

interface BalanceImageAnalyzer : AutoCloseable {
    suspend fun extractBalanceFromImage(
        imageReference: String,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<Double>

    override fun close() = Unit
}

