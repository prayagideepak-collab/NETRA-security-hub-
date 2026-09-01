package com.example.data.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedEventDao {
    @Query("SELECT * FROM unified_event_history ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getEventsPaginated(limit: Int, offset: Int): Flow<List<UnifiedEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: UnifiedEventEntity): Long

    @Query("SELECT * FROM unified_event_history WHERE eventName = :name AND sourceModule = :module AND status = :status ORDER BY timestamp DESC LIMIT 1")
    suspend fun findRecentDuplicate(name: String, module: String, status: String): UnifiedEventEntity?

    @Query("UPDATE unified_event_history SET occurrences = occurrences + 1, totalDurationMs = totalDurationMs + :duration WHERE id = :id")
    suspend fun updateEventDeduplication(id: Long, duration: Long)
}
