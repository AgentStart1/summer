package com.storytellerf.summer.data.recognition

import com.storytellerf.summer.data.llmd.LlmdTarget

interface FinanceImageAnalyzer : AutoCloseable {
    suspend fun extractBalanceFromImage(
        imageReference: String,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<Double>

    suspend fun extractBalanceWithImage(
        imageReference: String,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<RecognizedBalance> = extractBalanceFromImage(imageReference, target).map { RecognizedBalance(it, null) }

    suspend fun extractTransactionsFromImage(
        imageReference: String,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<RecognizedTransactions> = Result.failure(UnsupportedOperationException("Transaction recognition is unavailable"))

    override fun close() = Unit
}
