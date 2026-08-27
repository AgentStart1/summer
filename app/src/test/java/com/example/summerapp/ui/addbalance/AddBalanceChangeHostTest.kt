package com.example.summerapp.ui.addbalance

import com.example.summerapp.data.db.entity.FundSource
import com.example.summerapp.data.llmd.BalanceImageAnalyzer
import com.example.summerapp.data.llmd.LlmdAuthorizationException
import com.example.summerapp.data.llmd.LlmdTarget
import com.example.summerapp.testing.FakeDataRepository
import com.example.summerapp.testing.createHostTestEnvironment
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddBalanceChangeHostTest {
    @Test
    fun successfulAnalysisAndSave_useInjectedDispatchersAndPublishEffect() = runTest {
        val environment = createHostTestEnvironment()
        val fundSource = FundSource(id = 1, name = "Wallet")
        val repository = FakeDataRepository(
            fundSources = listOf(fundSource),
            expectedDefaultDispatcher = environment.defaultDispatcher,
            expectedIoDispatcher = environment.ioDispatcher,
        )
        val analyzer = FakeImageAnalyzer(
            expectedDispatcher = environment.ioDispatcher,
            result = Result.success(380.0),
        )
        val host = AddBalanceChangeHost(
            repository = repository,
            imageAnalyzer = analyzer,
            scope = environment.scope,
            dispatchers = environment.dispatchers,
            imageAnalysisTarget = flowOf(LlmdTarget.Daily),
        )
        val effects = mutableListOf<AddBalanceChangeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            host.uiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            host.effects.collect(effects::add)
        }

        advanceUntilIdle()
        assertEquals(listOf(fundSource), host.uiState.value.fundSources)

        host.extractBalanceFromImage("content://test/balance")
        advanceUntilIdle()
        assertEquals("380.00", host.uiState.value.balance)
        assertEquals("content://test/balance", analyzer.lastImageReference)
        assertEquals(LlmdTarget.Daily, analyzer.lastTarget)

        host.selectFundSource(fundSource)
        host.updateNote("Groceries")
        host.saveBalanceChange()
        advanceUntilIdle()
        environment.close()

        assertEquals(380.0, repository.insertedBalanceChanges.single().newBalance, 0.0)
        assertEquals("Groceries", repository.insertedBalanceChanges.single().note)
        assertTrue(AddBalanceChangeEffect.Saved in effects)
        assertEquals("", host.uiState.value.balance)
        assertFalse(host.uiState.value.isSaving)
        host.close()
        assertTrue(analyzer.closed)
    }

    @Test
    fun authorizationFailure_isPublishedAsOneTimeEffect() = runTest {
        val environment = createHostTestEnvironment()
        val analyzer = FakeImageAnalyzer(
            expectedDispatcher = environment.ioDispatcher,
            result = Result.failure(LlmdAuthorizationException()),
        )
        val host = AddBalanceChangeHost(
            repository = FakeDataRepository(
                expectedDefaultDispatcher = environment.defaultDispatcher,
                expectedIoDispatcher = environment.ioDispatcher,
            ),
            imageAnalyzer = analyzer,
            scope = environment.scope,
            dispatchers = environment.dispatchers,
            imageAnalysisTarget = flowOf(LlmdTarget.Debug),
        )
        val effects = mutableListOf<AddBalanceChangeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            host.effects.collect(effects::add)
        }

        host.extractBalanceFromImage("content://test/protected")
        advanceUntilIdle()
        environment.close()

        assertEquals(
            listOf(AddBalanceChangeEffect.RequestAuthorization(LlmdTarget.Debug)),
            effects,
        )
        assertFalse(host.uiState.value.isImageAnalyzing)
    }
}

private class FakeImageAnalyzer(
    private val expectedDispatcher: CoroutineDispatcher,
    private val result: Result<Double>,
) : BalanceImageAnalyzer {
    var lastImageReference: String? = null
    var lastTarget: LlmdTarget? = null
    var closed = false

    override suspend fun extractBalanceFromImage(
        imageReference: String,
        target: LlmdTarget,
    ): Result<Double> {
        check(currentCoroutineContext()[ContinuationInterceptor] === expectedDispatcher)
        lastImageReference = imageReference
        lastTarget = target
        return result
    }

    override fun close() {
        closed = true
    }
}
