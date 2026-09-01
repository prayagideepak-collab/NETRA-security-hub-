package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyEventDao {
    @Query("SELECT * FROM safety_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<SafetyEventEntity>>

    @Query("SELECT * FROM safety_events WHERE lifecycleState != 'RESOLVED' ORDER BY timestamp DESC")
    fun getActiveEvents(): Flow<List<SafetyEventEntity>>

    @Query("SELECT * FROM safety_events WHERE lifecycleState = 'RESOLVED' ORDER BY timestamp DESC")
    fun getResolvedEvents(): Flow<List<SafetyEventEntity>>

    @Query("SELECT * FROM safety_events WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getEventsByRisk(riskLevel: String): Flow<List<SafetyEventEntity>>

    @Query("SELECT * FROM safety_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventByEventId(eventId: String): SafetyEventEntity?

    @Query("SELECT * FROM safety_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): SafetyEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SafetyEventEntity): Long

    @Update
    suspend fun updateEvent(event: SafetyEventEntity)

    @Query("DELETE FROM safety_events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    @Query("DELETE FROM safety_events WHERE lifecycleState = 'RESOLVED' AND timestamp < :cutoffTimestamp")
    suspend fun pruneOldResolvedEvents(cutoffTimestamp: Long): Int

    @Query("DELETE FROM safety_events")
    suspend fun clearAllEvents()

    @Query("SELECT COUNT(*) FROM safety_events")
    suspend fun getEventCount(): Int
}
