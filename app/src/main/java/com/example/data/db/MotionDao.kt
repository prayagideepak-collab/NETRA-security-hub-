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

@Entity(tableName = "motion_route_sessions")
data class MotionRouteSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val dateKey: String,
    val activityCategory: String,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val startAccuracyMeters: Float?,
    val startTimestamp: Long?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val endAccuracyMeters: Float?,
    val endTimestamp: Long?,
    val snapshotDistanceMeters: Double?,
    val isCumulativeDistance: Boolean = false,
    val startSource: String? = null,
    val startSpeedKmH: Float? = null,
    val endSource: String? = null,
    val endSpeedKmH: Float? = null,
    val averageSpeedKmH: Float? = null,
    val maxSpeedKmH: Float? = null,
    val eventCount: Int? = null,
    val lastUpdatedMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "motion_route_events")
data class RouteEventEntity(
    @PrimaryKey
    val eventId: String,
    val sessionId: String,
    val dateKey: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val previousSpeedKmH: Float?,
    val currentSpeedKmH: Float?,
    val speedDeltaKmH: Float?,
    val headingBeforeDeg: Float?,
    val headingAfterDeg: Float?,
    val motionType: String,
    val classification: String,
    val confidence: String
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRouteSession(session: MotionRouteSessionEntity)

    @Query("SELECT * FROM motion_route_sessions WHERE dateKey = :dateKey ORDER BY startTimeMs DESC")
    suspend fun getRouteSessionsForDate(dateKey: String): List<MotionRouteSessionEntity>

    @Query("SELECT * FROM motion_route_sessions WHERE dateKey = :dateKey ORDER BY startTimeMs DESC")
    fun observeRouteSessionsForDate(dateKey: String): Flow<List<MotionRouteSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteEvent(event: RouteEventEntity)

    @Query("SELECT * FROM motion_route_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRouteEventsForSession(sessionId: String): List<RouteEventEntity>

    @Query("SELECT * FROM motion_route_events WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getRouteEventsForDate(dateKey: String): List<RouteEventEntity>

    @Query("DELETE FROM daily_motion_summaries WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldSummaries()

    @Query("DELETE FROM motion_events WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldEvents()

    @Query("DELETE FROM motion_route_sessions WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldRouteSessions()

    @Query("DELETE FROM motion_route_events WHERE dateKey NOT IN (SELECT dateKey FROM daily_motion_summaries ORDER BY dateKey DESC LIMIT 7)")
    suspend fun pruneOldRouteEvents()

    @Query("DELETE FROM motion_route_events WHERE sessionId NOT IN (SELECT sessionId FROM motion_route_sessions)")
    suspend fun pruneOrphanRouteEvents()
}
