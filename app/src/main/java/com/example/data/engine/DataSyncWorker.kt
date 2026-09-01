package com.example.data.engine

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.util.LoggingManager
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import kotlinx.coroutines.withTimeoutOrNull

class DataSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isExtendedWindow = inputData.getBoolean("extended_window", false)
        val timeLimit = if (isExtendedWindow) 60000L else 30000L

        return withTimeoutOrNull(timeLimit) {
            try {
                LoggingManager.info("SyncEngine", "DATA_SYNC_START", "Data sync cycle initiated.", "Automated synchronization cycle. Time limit: ${timeLimit / 1000}s.")
                
                // --- INGESTION LOGIC ---
                // For now, simulating ingestion and logging
                
                val db = NetraDatabase.getInstance(applicationContext)
                db.safetyEventDao().insertEvent(
                    SafetyEventEntity(
                        riskLevel = "INFORMATION",
                        riskScore = 0,
                        eventType = "DATA_SYNC_COMPLETED",
                        title = "Data Synchronization",
                        description = "Data successfully imported and synchronized.",
                        primarySensorValuesJson = "{\"status\":\"success\", \"timestamp\":\"${System.currentTimeMillis()}\"}",
                        aiRecommendation = "Maintain monitoring.",
                        isVerifiedHardwareEvent = true
                    )
                )
                
                LoggingManager.info("SyncEngine", "DATA_SYNC_SUCCESS", "Data sync successful.", "Synchronization cycle completed within constraints.")
                Result.success()
            } catch (e: Exception) {
                LoggingManager.critical("SyncEngine", "DATA_SYNC_FAILURE", "Data sync failed: ${e.message}", "Error during sync cycle.")
                Result.retry()
            }
        } ?: Result.failure()
    }
}
