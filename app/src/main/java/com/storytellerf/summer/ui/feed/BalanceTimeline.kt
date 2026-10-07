package com.storytellerf.summer.ui.feed

import com.storytellerf.summer.data.TimelinePage
import com.storytellerf.summer.data.db.entity.BalanceChange
import com.storytellerf.summer.data.db.entity.BalanceImpactRecord
import com.storytellerf.summer.data.db.entity.FundSource
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface TimelineItem {
    val key: String
    val timestamp: Long
    data class Snapshot(val snapshot: BalanceSnapshot, val isLatest: Boolean) : TimelineItem {
        override val key = "snapshot:${snapshot.id}"
        override val timestamp = snapshot.timestamp
    }
    data class Transaction(val record: BalanceImpactRecord, val fundSourceName: String) : TimelineItem {
        override val key = "transaction:${record.id}"
        override val timestamp = record.timestamp
    }
    data class Difference(
        val balanceChangeId: Long,
        override val timestamp: Long,
        val amount: Double,
        val fundSourceName: String,
    ) : TimelineItem {
        override val key = "difference:$balanceChangeId"
    }
}

data class BalanceSnapshot(
    val id: Long,
    val timestamp: Long,
    val totalBalance: Double,
    val fundBalances: List<FundBalance>,
)

data class FundBalance(val fundSourceId: Long, val fundSourceName: String, val balance: Double)

internal fun buildBalanceTimeline(
    balanceChanges: List<BalanceChange>,
    fundSources: List<FundSource>,
    precedingBalances: List<BalanceChange> = emptyList(),
): List<BalanceSnapshot> {
    val sourceById = fundSources.associateBy(FundSource::id)
    val orderedSources = fundSources.sortedWith(compareBy(FundSource::createdAt, FundSource::name, FundSource::id))
    val balances = precedingBalances.associate { it.fundSourceId to it.newBalance }.toMutableMap()
    return balanceChanges.sortedWith(compareBy(BalanceChange::timestamp, BalanceChange::id)).map { change ->
        balances[change.fundSourceId] = change.newBalance
        val funds = orderedSources.mapNotNull { source ->
            balances[source.id]?.let { FundBalance(source.id, sourceById.getValue(source.id).name, it) }
        }
        BalanceSnapshot(change.id, change.timestamp, funds.sumOf(FundBalance::balance), funds)
    }.asReversed()
}

internal fun flattenTimeline(page: TimelinePage, isFirstPage: Boolean): List<TimelineItem> {
    val snapshots = buildBalanceTimeline(page.changes, page.fundSources, page.precedingBalances)
    val names = page.fundSources.associate { it.id to it.name }
    val previous = page.precedingBalances.associate { it.fundSourceId to it.newBalance }.toMutableMap()
    val differences = page.changes.sortedWith(compareBy(BalanceChange::timestamp, BalanceChange::id)).mapNotNull { change ->
        val baseline = previous.put(change.fundSourceId, change.newBalance) ?: change.previousBalance
        baseline?.let {
            val remaining = BigDecimal.valueOf(change.newBalance).subtract(BigDecimal.valueOf(it))
                .subtract(BigDecimal.valueOf(change.coveredOrderAmount)).setScale(2, RoundingMode.HALF_UP)
            if (remaining.signum() == 0) null else TimelineItem.Difference(
                change.id, change.timestamp, remaining.toDouble(), names[change.fundSourceId] ?: "Unknown fund")
        }
    }
    return (snapshots.mapIndexed { index, snapshot ->
        TimelineItem.Snapshot(snapshot, isFirstPage && index == 0)
    } + differences + page.records.map { record ->
        TimelineItem.Transaction(record, names[record.fundSourceId] ?: "Unknown fund")
    }).sortedWith(compareByDescending<TimelineItem> { it.timestamp }
        .thenBy { if (it is TimelineItem.Snapshot) 0 else 1 }
        .thenByDescending { when (it) {
            is TimelineItem.Snapshot -> it.snapshot.id
            is TimelineItem.Transaction -> it.record.id
            is TimelineItem.Difference -> it.balanceChangeId
        } })
}
