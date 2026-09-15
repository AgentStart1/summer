package com.storytellerf.summet.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.storytellerf.summet.data.db.dao.BalanceChangeDao
import com.storytellerf.summet.data.db.dao.FundSourceDao
import com.storytellerf.summet.data.db.entity.BalanceChange
import com.storytellerf.summet.data.db.entity.FundSource

@Database(
    entities = [FundSource::class, BalanceChange::class],
    version = 1,
    exportSchema = false,
)
abstract class SummerDatabase : RoomDatabase() {
    abstract fun fundSourceDao(): FundSourceDao
    abstract fun balanceChangeDao(): BalanceChangeDao

    companion object {
        @Volatile
        private var instance: SummerDatabase? = null

        fun getInstance(context: Context): SummerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SummerDatabase::class.java,
                    "summer_finance.db",
                ).build().also { instance = it }
            }
        }
    }
}
