package com.storytellerf.summer.data

import androidx.room.withTransaction
import com.storytellerf.summer.data.db.SummerDatabase
import com.storytellerf.summer.data.db.entity.BalanceImpactRecord
import kotlinx.coroutines.flow.map
import com.storytellerf.summer.data.db.entity.BalanceChange
import com.storytellerf.summer.data.db.entity.FundSource
import kotlinx.coroutines.flow.Flow

interface DataRepository {
    fun observeTimelineChanges(): Flow<Unit>
    suspend fun loadTimelinePage(offset: Int, snapshotCount: Int): TimelinePage
    suspend fun importTransactions(records: List<BalanceImpactRecord>): Int

    // Fund Sources
    fun getAllFundSources(): Flow<List<FundSource>>
    suspend fun getFundSourceById(id: Long): FundSource?
    suspend fun insertFundSource(fundSource: FundSource): Long
    suspend fun updateFundSource(fundSource: FundSource)
    suspend fun deleteFundSource(fundSource: FundSource)

    // Balance Changes
    fun getAllBalanceChanges(): Flow<List<BalanceChange>>
    fun getBalanceChangesByFundSource(fundSourceId: Long): Flow<List<BalanceChange>>
    suspend fun getBalanceChangeById(id: Long): BalanceChange?
    suspend fun insertBalanceChange(balanceChange: BalanceChange): Long
    suspend fun updateBalanceChange(balanceChange: BalanceChange)
    suspend fun deleteBalanceChange(balanceChange: BalanceChange)
}

class DefaultDataRepository(private val database: SummerDatabase) : DataRepository {
    override fun observeTimelineChanges(): Flow<Unit> = database.invalidationTracker
        .createFlow("fund_sources", "balance_changes", "balance_impact_records").map { }

    override suspend fun importTransactions(records: List<BalanceImpactRecord>): Int {
        require(records.all { it.amount.isFinite() && it.timestamp > 0 && it.imageHash.isNotBlank() && it.imageRow >= 0 })
        val normalized = records.map { record ->
            val id = record.transactionId?.trim()?.takeIf(String::isNotEmpty)
            record.copy(transactionId = id, imageDedupKey = if (id == null) "${record.imageHash}:${record.imageRow}" else null)
        }
        return database.withTransaction {
            val inserted = database.balanceImpactRecordDao().insertAll(normalized)
            normalized.zip(inserted).filter { it.second != -1L }.map { it.first.fundSourceId }.distinct()
                .forEach { balanceChangeDao.recomputeOrderCoverage(it) }
            inserted.count { it != -1L }
        }
    }

    override suspend fun loadTimelinePage(offset: Int, snapshotCount: Int): TimelinePage = database.withTransaction {
        require(offset >= 0 && snapshotCount > 0)
        val window = balanceChangeDao.getPage(offset, snapshotCount + 1)
        val changes = window.take(snapshotCount)
        val hasMore = window.size > snapshotCount
        // With no snapshot anchors, still page transaction-only timelines instead of loading every row.
        if (changes.isEmpty() && balanceChangeDao.getPage(0, 1).isEmpty()) {
            val transactions = database.balanceImpactRecordDao().getPage(offset, snapshotCount + 1)
            return@withTransaction TimelinePage(emptyList(), emptyList(), fundSourceDao.getAllOnce(),
                transactions.take(snapshotCount), transactions.size > snapshotCount)
        }
        val oldest = changes.lastOrNull()
        val upper = if (offset == 0) null else balanceChangeDao.getPage(offset - 1, 1).firstOrNull()?.timestamp
        TimelinePage(
            changes = changes,
            precedingBalances = oldest?.let { balanceChangeDao.getBalancesBefore(it.timestamp, it.id) }.orEmpty(),
            fundSources = fundSourceDao.getAllOnce(),
            records = database.balanceImpactRecordDao().getInRange(if (hasMore) oldest?.timestamp else null, upper),
            hasMore = hasMore,
        )
    }

    private val fundSourceDao = database.fundSourceDao()
    private val balanceChangeDao = database.balanceChangeDao()

    override fun getAllFundSources(): Flow<List<FundSource>> = fundSourceDao.getAll()
    override suspend fun getFundSourceById(id: Long): FundSource? = fundSourceDao.getById(id)
    override suspend fun insertFundSource(fundSource: FundSource): Long = fundSourceDao.insert(fundSource)
    override suspend fun updateFundSource(fundSource: FundSource) = fundSourceDao.update(fundSource)
    override suspend fun deleteFundSource(fundSource: FundSource) = fundSourceDao.delete(fundSource)

    override fun getAllBalanceChanges(): Flow<List<BalanceChange>> = balanceChangeDao.getAll()
    override fun getBalanceChangesByFundSource(fundSourceId: Long): Flow<List<BalanceChange>> =
        balanceChangeDao.getByFundSource(fundSourceId)
    override suspend fun getBalanceChangeById(id: Long): BalanceChange? = balanceChangeDao.getById(id)
    override suspend fun insertBalanceChange(balanceChange: BalanceChange): Long =
        database.withTransaction {
            val oldSource = balanceChange.id.takeIf { it != 0L }?.let { balanceChangeDao.getById(it)?.fundSourceId }
            val id = balanceChangeDao.insert(balanceChange.copy(coveredOrderAmount = 0.0))
            listOfNotNull(oldSource, balanceChange.fundSourceId).distinct().forEach { balanceChangeDao.recomputeOrderCoverage(it) }
            id
        }
    override suspend fun updateBalanceChange(balanceChange: BalanceChange) =
        database.withTransaction {
            val oldSource = balanceChangeDao.getById(balanceChange.id)?.fundSourceId
            balanceChangeDao.update(balanceChange.copy(coveredOrderAmount = 0.0))
            listOfNotNull(oldSource, balanceChange.fundSourceId).distinct().forEach { balanceChangeDao.recomputeOrderCoverage(it) }
        }
    override suspend fun deleteBalanceChange(balanceChange: BalanceChange) =
        database.withTransaction {
            val oldSource = balanceChangeDao.getById(balanceChange.id)?.fundSourceId
            balanceChangeDao.delete(balanceChange)
            oldSource?.let { balanceChangeDao.recomputeOrderCoverage(it) }
            Unit
        }
}

/** A consistent database window plus the account balances needed to reconstruct its snapshots. */
data class TimelinePage(
    val changes: List<BalanceChange>,
    val precedingBalances: List<BalanceChange>,
    val fundSources: List<FundSource>,
    val records: List<BalanceImpactRecord>,
    val hasMore: Boolean,
)
