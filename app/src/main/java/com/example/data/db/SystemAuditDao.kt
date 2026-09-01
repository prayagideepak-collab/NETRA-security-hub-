package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemAuditDao {
    @Query("SELECT * FROM system_audits ORDER BY timestamp DESC")
    fun getAllAudits(): Flow<List<SystemAuditEntity>>

    @Query("SELECT * FROM system_audits WHERE id = :id LIMIT 1")
    suspend fun getAuditById(id: Long): SystemAuditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: SystemAuditEntity): Long

    @Query("DELETE FROM system_audits WHERE id = :id")
    suspend fun deleteAuditById(id: Long)

    @Query("DELETE FROM system_audits")
    suspend fun clearAllAudits()
}
