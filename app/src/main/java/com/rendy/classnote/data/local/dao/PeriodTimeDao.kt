package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.PeriodTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodTimeDao {

    @Query("SELECT * FROM period_times ORDER BY period")
    fun getAllPeriodTimes(): Flow<List<PeriodTimeEntity>>

    @Query("SELECT * FROM period_times WHERE period = :period LIMIT 1")
    suspend fun getPeriodTime(period: Int): PeriodTimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(periodTime: PeriodTimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periodTimes: List<PeriodTimeEntity>)

    @Query("SELECT * FROM period_times ORDER BY period")
    suspend fun getAllPeriodTimesOnce(): List<PeriodTimeEntity>
}
