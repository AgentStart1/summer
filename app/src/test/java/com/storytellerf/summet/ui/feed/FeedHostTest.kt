package com.storytellerf.summet.ui.feed

import com.storytellerf.summet.data.db.entity.BalanceChange
import com.storytellerf.summet.data.db.entity.FundSource
import com.storytellerf.summet.testing.FakeDataRepository
import com.storytellerf.summet.testing.createHostTestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedHostTest {
    @Test
    fun timelineTransformation_runsOnInjectedDefaultDispatcher() = runTest {
        val environment = createHostTestEnvironment()
        val source = FundSource(id = 1, name = "Wallet")
        val repository = FakeDataRepository(
            fundSources = listOf(source),
            balanceChanges = listOf(
                BalanceChange(
                    id = 1,
                    fundSourceId = source.id,
                    newBalance = 500.0,
                    note = "Opening balance",
                    timestamp = 1_000,
                )
            ),
            expectedDefaultDispatcher = environment.defaultDispatcher,
        )
        val host = FeedHost(repository, environment.scope, environment.dispatchers)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            host.uiState.collect()
        }

        advanceUntilIdle()
        environment.close()

        val state = host.uiState.value
        assertTrue(state is FeedUiState.Success)
        val snapshot = (state as FeedUiState.Success).snapshots.single()
        assertEquals(500.0, snapshot.totalBalance, 0.0)
        assertEquals("Opening balance", snapshot.record.note)
    }
}
