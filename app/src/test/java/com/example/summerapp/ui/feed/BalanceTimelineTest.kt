package com.example.summerapp.ui.feed

import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceTimelineTest {
    @Test
    fun timeline_rebuildsEachSnapshotAndItsImpactRecord() {
        val bank = FundSource(id = 1, name = "Bank", createdAt = 1)
        val wallet = FundSource(id = 2, name = "Wallet", createdAt = 2)
        val changes = listOf(
            BalanceChange(id = 3, fundSourceId = wallet.id, newBalance = 80.0, note = "Groceries", timestamp = 30),
            BalanceChange(id = 1, fundSourceId = bank.id, newBalance = 1_000.0, note = "Opening balance", timestamp = 10),
            BalanceChange(id = 2, fundSourceId = wallet.id, newBalance = 200.0, note = "Cash received", timestamp = 20),
        )

        val snapshots = buildBalanceTimeline(changes, listOf(wallet, bank))

        assertEquals(listOf(3L, 2L, 1L), snapshots.map(BalanceSnapshot::id))
        assertEquals(1_080.0, snapshots[0].totalBalance, 0.0)
        assertEquals(
            listOf("Bank" to 1_000.0, "Wallet" to 80.0),
            snapshots[0].fundBalances.map { it.fundSourceName to it.balance },
        )
        assertEquals("Groceries", snapshots[0].record.note)
        assertEquals(-120.0, snapshots[0].record.amount, 0.0)
        assertEquals(1_200.0, snapshots[1].totalBalance, 0.0)
        assertEquals(200.0, snapshots[1].record.amount, 0.0)
        assertEquals(1_000.0, snapshots[2].totalBalance, 0.0)
    }

    @Test
    fun blankNote_isRemovedFromUiReadyRecord() {
        val source = FundSource(id = 1, name = "Savings")
        val snapshots = buildBalanceTimeline(
            balanceChanges = listOf(
                BalanceChange(id = 1, fundSourceId = source.id, newBalance = 50.0, note = "  ", timestamp = 1)
            ),
            fundSources = listOf(source),
        )

        assertEquals(null, snapshots.single().record.note)
        assertEquals(50.0, snapshots.single().record.amount, 0.0)
    }
}
