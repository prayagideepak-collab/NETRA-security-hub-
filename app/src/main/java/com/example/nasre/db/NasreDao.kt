package com.example.nasre.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NasreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthMonitor(entity: HealthMonitorEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosticLog(entity: DiagnosticLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResourceOptimizer(entity: ResourceOptimizerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRootCause(entity: RootCauseEntity): Long

    @Query("SELECT * FROM health_monitor ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentHealthLogs(): List<HealthMonitorEntity>

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 500")
    suspend fun getRecentDiagnosticLogs(): List<DiagnosticLogEntity>
}
