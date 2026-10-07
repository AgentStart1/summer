package com.storytellerf.summer.ui.feed

import com.storytellerf.summer.data.TimelinePage
import com.storytellerf.summer.data.db.entity.BalanceChange
import com.storytellerf.summer.data.db.entity.BalanceImpactRecord
import com.storytellerf.summer.data.db.entity.FundSource
import org.junit.Assert.*
import org.junit.Test

class BalanceTimelineTest {
    @Test fun uncoveredDifferenceUsesPersistedCoverage_includingOvercoverageAndRounding() {
        val source = FundSource(id = 1, name = "Bank")
        val seed = BalanceChange(id = 1, fundSourceId = 1, newBalance = 1000.0, timestamp = 10)
        val change = BalanceChange(id = 2, fundSourceId = 1, newBalance = 900.0, timestamp = 20)
        fun difference(covered: Double) = flattenTimeline(TimelinePage(
            listOf(change.copy(coveredOrderAmount = covered)), listOf(seed), listOf(source), emptyList(), false), false)
            .filterIsInstance<TimelineItem.Difference>()
        assertEquals(-100.0, difference(0.0).single().amount, 0.0)
        assertEquals(-30.0, difference(-70.0).single().amount, 0.0)
        assertTrue(difference(-100.0).isEmpty())
        assertEquals(20.0, difference(-120.0).single().amount, 0.0)
        assertTrue(difference(-99.99999999999999).isEmpty())
    }

    @Test fun differencesUseActualPrecedingAccountBalances_acrossPageBoundary() {
        val sources = listOf(FundSource(id = 1, name = "Bank"), FundSource(id = 2, name = "Wallet"))
        val page = TimelinePage(listOf(
            BalanceChange(id = 3, fundSourceId = 1, newBalance = 900.0, previousBalance = 123.0, timestamp = 30, coveredOrderAmount = -70.0),
            BalanceChange(id = 4, fundSourceId = 2, newBalance = 20.0, timestamp = 40),
            BalanceChange(id = 5, fundSourceId = 1, newBalance = 850.0, timestamp = 50, coveredOrderAmount = -50.0)),
            listOf(BalanceChange(id = 1, fundSourceId = 1, newBalance = 1000.0, timestamp = 10),
                BalanceChange(id = 2, fundSourceId = 2, newBalance = 30.0, timestamp = 20)), sources, emptyList(), false)
        val differences = flattenTimeline(page, false).filterIsInstance<TimelineItem.Difference>()
        assertEquals(listOf(-10.0, -30.0), differences.map { it.amount })
        assertEquals(listOf("Wallet", "Bank"), differences.map { it.fundSourceName })
    }

    @Test fun olderPage_reconstructsTotalsWithAccountsOutsideItsWindow() {
        val bank = FundSource(id = 1, name = "Bank", createdAt = 1)
        val wallet = FundSource(id = 2, name = "Wallet", createdAt = 2)
        val snapshots = buildBalanceTimeline(
            listOf(BalanceChange(id = 3, fundSourceId = 2, newBalance = 80.0, timestamp = 30)),
            listOf(wallet, bank),
            listOf(BalanceChange(id = 1, fundSourceId = 1, newBalance = 1000.0, timestamp = 10),
                BalanceChange(id = 2, fundSourceId = 2, newBalance = 200.0, timestamp = 20)),
        )
        assertEquals(1080.0, snapshots.single().totalBalance, 0.0)
        assertEquals(listOf("Bank", "Wallet"), snapshots.single().fundBalances.map { it.fundSourceName })
    }

    @Test fun snapshotsDoNotGenerateTransactions_andImportedAmountsArePreserved() {
        val source = FundSource(id = 1, name = "Bank")
        val change = BalanceChange(id = 1, fundSourceId = 1, newBalance = 2000.0, timestamp = 30)
        val page = TimelinePage(listOf(change), emptyList(), listOf(source), emptyList(), false)
        assertEquals(1, flattenTimeline(page, true).size)
        val record = BalanceImpactRecord(id = 1, fundSourceId = 1, timestamp = 20, amount = -17.5,
            note = "Purchase", imageHash = "image", imageRow = 0)
        val items = flattenTimeline(page.copy(records = listOf(record)), true)
        assertTrue((items.first() as TimelineItem.Snapshot).isLatest)
        assertEquals(-17.5, (items.last() as TimelineItem.Transaction).record.amount, 0.0)
        assertEquals(2, items.map { it.key }.distinct().size) // Equal entity IDs have distinct UI keys.
    }

    @Test fun sameTimestampSnapshots_useIdToReconstructDeterministically() {
        val snapshots = buildBalanceTimeline(listOf(
            BalanceChange(id = 2, fundSourceId = 1, newBalance = 50.0, timestamp = 10),
            BalanceChange(id = 1, fundSourceId = 1, newBalance = 100.0, timestamp = 10)),
            listOf(FundSource(id = 1, name = "Bank")))
        assertEquals(listOf(50.0, 100.0), snapshots.map { it.totalBalance })
    }
}
