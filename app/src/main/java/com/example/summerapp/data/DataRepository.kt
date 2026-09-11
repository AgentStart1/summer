package com.example.summerapp.data

import com.example.summerapp.data.db.SummerDatabase
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import kotlinx.coroutines.flow.Flow

interface DataRepository {
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
        balanceChangeDao.insert(balanceChange)
    override suspend fun updateBalanceChange(balanceChange: BalanceChange) =
        balanceChangeDao.update(balanceChange)
    override suspend fun deleteBalanceChange(balanceChange: BalanceChange) =
        balanceChangeDao.delete(balanceChange)
}
