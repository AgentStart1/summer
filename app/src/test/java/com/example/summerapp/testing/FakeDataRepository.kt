package com.example.summerapp.testing

import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class FakeDataRepository(
    fundSources: List<FundSource> = emptyList(),
    balanceChanges: List<BalanceChange> = emptyList(),
    private val expectedDefaultDispatcher: CoroutineDispatcher? = null,
    private val expectedIoDispatcher: CoroutineDispatcher? = null,
) : DataRepository {
    private val mutableFundSources = MutableStateFlow(fundSources)
    private val mutableBalanceChanges = MutableStateFlow(balanceChanges)

    val insertedBalanceChanges = mutableListOf<BalanceChange>()
    val insertedFundSources = mutableListOf<FundSource>()
    val updatedFundSources = mutableListOf<FundSource>()
    val deletedFundSources = mutableListOf<FundSource>()

    override fun getAllFundSources(): Flow<List<FundSource>> = mutableFundSources
        .onEach { assertDispatcher(expectedDefaultDispatcher) }

    override suspend fun getFundSourceById(id: Long): FundSource? {
        assertDispatcher(expectedIoDispatcher)
        return mutableFundSources.value.firstOrNull { it.id == id }
    }

    override suspend fun insertFundSource(fundSource: FundSource): Long {
        assertDispatcher(expectedIoDispatcher)
        val inserted = fundSource.copy(id = fundSource.id.takeIf { it != 0L } ?: nextFundSourceId())
        insertedFundSources += inserted
        mutableFundSources.value += inserted
        return inserted.id
    }

    override suspend fun updateFundSource(fundSource: FundSource) {
        assertDispatcher(expectedIoDispatcher)
        updatedFundSources += fundSource
        mutableFundSources.value = mutableFundSources.value.map {
            if (it.id == fundSource.id) fundSource else it
        }
    }

    override suspend fun deleteFundSource(fundSource: FundSource) {
        assertDispatcher(expectedIoDispatcher)
        deletedFundSources += fundSource
        mutableFundSources.value = mutableFundSources.value.filterNot { it.id == fundSource.id }
    }

    override fun getAllBalanceChanges(): Flow<List<BalanceChange>> = mutableBalanceChanges
        .onEach { assertDispatcher(expectedDefaultDispatcher) }

    override fun getBalanceChangesByFundSource(fundSourceId: Long): Flow<List<BalanceChange>> =
        mutableBalanceChanges.onEach { assertDispatcher(expectedIoDispatcher) }
            .map { changes ->
                changes.filter { it.fundSourceId == fundSourceId }
            }

    override suspend fun getBalanceChangeById(id: Long): BalanceChange? {
        assertDispatcher(expectedIoDispatcher)
        return mutableBalanceChanges.value.firstOrNull { it.id == id }
    }

    override suspend fun insertBalanceChange(balanceChange: BalanceChange): Long {
        assertDispatcher(expectedIoDispatcher)
        val inserted = balanceChange.copy(
            id = balanceChange.id.takeIf { it != 0L } ?: nextBalanceChangeId(),
        )
        insertedBalanceChanges += inserted
        mutableBalanceChanges.value = listOf(inserted) + mutableBalanceChanges.value
        return inserted.id
    }

    override suspend fun updateBalanceChange(balanceChange: BalanceChange) {
        assertDispatcher(expectedIoDispatcher)
        mutableBalanceChanges.value = mutableBalanceChanges.value.map {
            if (it.id == balanceChange.id) balanceChange else it
        }
    }

    override suspend fun deleteBalanceChange(balanceChange: BalanceChange) {
        assertDispatcher(expectedIoDispatcher)
        mutableBalanceChanges.value = mutableBalanceChanges.value.filterNot {
            it.id == balanceChange.id
        }
    }

    private fun nextFundSourceId(): Long =
        (mutableFundSources.value.maxOfOrNull(FundSource::id) ?: 0L) + 1L

    private fun nextBalanceChangeId(): Long =
        (mutableBalanceChanges.value.maxOfOrNull(BalanceChange::id) ?: 0L) + 1L
}

private suspend fun assertDispatcher(expected: CoroutineDispatcher?) {
    if (expected == null) return
    check(currentCoroutineContext()[ContinuationInterceptor] === expected) {
        "Expected work on $expected but was on " +
            currentCoroutineContext()[ContinuationInterceptor]
    }
}
