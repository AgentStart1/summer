package com.storytellerf.summer.ui.feed

import com.storytellerf.summer.data.db.entity.BalanceChange
import com.storytellerf.summer.data.db.entity.FundSource

data class BalanceSnapshot(
    val id: Long,
    val timestamp: Long,
    val totalBalance: Double,
    val fundBalances: List<FundBalance>,
    val record: BalanceImpactRecord,
)

data class FundBalance(
    val fundSourceId: Long,
    val fundSourceName: String,
    val balance: Double,
)

data class BalanceImpactRecord(
    val timestamp: Long,
    val fundSourceName: String,
    val note: String?,
    val amount: Double,
)

internal fun buildBalanceTimeline(
    balanceChanges: List<BalanceChange>,
    fundSources: List<FundSource>,
): List<BalanceSnapshot> {
    val sourceById = fundSources.associateBy(FundSource::id)
    val sourceOrder = fundSources
        .sortedWith(compareBy(FundSource::createdAt, FundSource::name))
        .mapIndexed { index, source -> source.id to index }
        .toMap()
    val balances = mutableMapOf<Long, Double>()

    return balanceChanges
        .sortedWith(compareBy(BalanceChange::timestamp, BalanceChange::id))
        .map { change ->
            val previousBalance = balances[change.fundSourceId] ?: 0.0
            balances[change.fundSourceId] = change.newBalance
            val fundBalances = balances.entries
                .sortedWith(
                    compareBy<Map.Entry<Long, Double>>(
                        { sourceOrder[it.key] ?: Int.MAX_VALUE },
                        { sourceById[it.key]?.name.orEmpty() },
                    )
                )
                .map { (sourceId, balance) ->
                    FundBalance(
                        fundSourceId = sourceId,
                        fundSourceName = sourceById[sourceId]?.name ?: "Unknown fund",
                        balance = balance,
                    )
                }

            BalanceSnapshot(
                id = change.id,
                timestamp = change.timestamp,
                totalBalance = fundBalances.sumOf(FundBalance::balance),
                fundBalances = fundBalances,
                record = BalanceImpactRecord(
                    timestamp = change.timestamp,
                    fundSourceName = sourceById[change.fundSourceId]?.name ?: "Unknown fund",
                    note = change.note?.takeIf(String::isNotBlank),
                    amount = change.newBalance - previousBalance,
                ),
            )
        }
        .asReversed()
}
