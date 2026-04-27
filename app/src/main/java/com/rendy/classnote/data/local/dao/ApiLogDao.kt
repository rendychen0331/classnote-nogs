package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ApiLogEntity

@Dao
interface ApiLogDao {
    @Query("SELECT * FROM api_logs ORDER BY timestamp DESC LIMIT 200")
    suspend fun getRecentLogs(): List<ApiLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ApiLogEntity)

    @Query("DELETE FROM api_logs WHERE id NOT IN (SELECT id FROM api_logs ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneOldLogs()

    @Query("DELETE FROM api_logs")
    suspend fun clearAll()
}
