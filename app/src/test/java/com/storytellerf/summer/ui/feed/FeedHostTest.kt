package com.storytellerf.summer.ui.feed

import androidx.paging.testing.asSnapshot
import com.storytellerf.summer.data.db.entity.BalanceChange
import com.storytellerf.summer.data.db.entity.FundSource
import com.storytellerf.summer.testing.FakeDataRepository
import com.storytellerf.summer.testing.createHostTestEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FeedHostTest {
    @Test fun pagerLoadsBoundedSnapshotWindowsOnIo_andCancelsWithHost() = runTest {
        val environment = createHostTestEnvironment()
        val repository = FakeDataRepository(fundSources = listOf(FundSource(id = 1, name = "Wallet")),
            balanceChanges = (1L..45L).map { BalanceChange(id = it, fundSourceId = 1, newBalance = it.toDouble(), timestamp = it) },
            expectedIoDispatcher = environment.ioDispatcher)
        val host = FeedHost(repository, environment.scope, environment.dispatchers)
        try {
            val items = host.items.asSnapshot { scrollTo(88) }
            assertEquals(89, items.size)
            assertEquals(45.0, (items.first() as TimelineItem.Snapshot).snapshot.totalBalance, 0.0)
            assertEquals(1.0, (items.last() as TimelineItem.Snapshot).snapshot.totalBalance, 0.0)
            assertEquals(listOf(0, 20, 40), repository.requestedOffsets.distinct())
        } finally { host.close(); environment.close() }
    }
}
