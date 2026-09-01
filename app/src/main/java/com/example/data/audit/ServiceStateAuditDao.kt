package com.example.data.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceStateAuditDao {
    @Insert
    suspend fun insertAuditRecord(record: ServiceStateAuditEntity)

    @Query("SELECT * FROM service_state_audit ORDER BY timestamp DESC")
    fun getAllAuditRecords(): Flow<List<ServiceStateAuditEntity>>

    @Query("DELETE FROM service_state_audit")
    suspend fun clearAllRecords()
}
