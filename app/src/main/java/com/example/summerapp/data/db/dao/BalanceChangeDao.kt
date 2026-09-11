package com.example.summerapp.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.summerapp.data.db.entity.BalanceChange
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceChangeDao {
    @Query("SELECT * FROM balance_changes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BalanceChange>>

    @Query("SELECT * FROM balance_changes WHERE fundSourceId = :fundSourceId ORDER BY timestamp DESC")
    fun getByFundSource(fundSourceId: Long): Flow<List<BalanceChange>>

    @Query("SELECT * FROM balance_changes WHERE id = :id")
    suspend fun getById(id: Long): BalanceChange?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balanceChange: BalanceChange): Long

    @Update
    suspend fun update(balanceChange: BalanceChange)

    @Delete
    suspend fun delete(balanceChange: BalanceChange)
}
