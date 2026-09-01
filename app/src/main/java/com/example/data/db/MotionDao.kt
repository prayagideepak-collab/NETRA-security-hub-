package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_motion_summaries")
data class DailyMotionSummaryEntity(
    @PrimaryKey
    val dateKey: String, // e.g. "2026-09-01"
    val totalSteps: Int,
    val totalDistanceMeters: Double,
    val totalActiveTimeSec: Long,
    val walkingDurationSec: Long,
    val walkingSteps: Int,
    val walkingDistanceMeters: Double,
    val runningDurationSec: Long,
    val runningSteps: Int,
    val runningDistanceMeters: Double,
    val drivingDurationSec: Long,
    val drivingDistanceMeters: Double,
    val standingDurationSec: Long,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "motion_events")
data class MotionEventEntity(
    @PrimaryKey
    val eventId: String,
    val category: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSec: Long,
    val confidence: String,
    val sourceSensors: String,
    val distanceMeters: Double?,
    val stepCount: Int?,
    val timestamp: Long,
    val dataQuality: String,
    val dateKey: String
)

@Dao
interface MotionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySummary(summary: DailyMotionSummaryEntity)

    @Query("SELECT * FROM daily_motion_summaries WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailySummary(dateKey: String): DailyMotionSummaryEntity?

    @Query("SELECT * FROM daily_motion_summaries WHERE dateKey = :dateKey LIMIT 1")
    fun observeDailySummary(dateKey: String): Flow<DailyMotionSummaryEntity?>

    @Query("SELECT DISTINCT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7")
    suspend fun getAvailableHistoryDates(): List<String>

    @Query("SELECT DISTINCT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7")
    fun observeAvailableHistoryDates(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMotionEvent(event: MotionEventEntity)

    @Query("SELECT * FROM motion_events WHERE dateKey = :dateKey ORDER BY startTimeMs DESC LIMIT 50")
    suspend fun getEventsForDate(dateKey: String): List<MotionEventEntity>

    @Query("DELETE FROM daily_motion_summaries WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldSummaries()

    @Query("DELETE FROM motion_events WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldEvents()
}
