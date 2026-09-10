package com.example.data.engine

import android.content.Context
import android.os.BatteryManager as AndroidBatteryManager
import androidx.work.*
import com.example.data.pipeline.DeviceDataSyncManager
import com.example.util.LoggingManager
import java.util.concurrent.TimeUnit

class TelemetryUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Verify power / charging condition for ultra-low power architecture
            val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? AndroidBatteryManager
            val isCharging = batteryManager?.isCharging == true
            val batteryLevel = batteryManager?.getIntProperty(AndroidBatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50

            // Ensure execution only occurs when device is charging or has sufficient battery (>= 20%)
            if (!isCharging && batteryLevel < 20) {
                LoggingManager.info("TelemetryWorker", "TELEMETRY_UPLOAD_DEFERRED", "Battery level low ($batteryLevel%) and not charging. Deferring non-urgent telemetry upload.", "Ultra-low power conservation rule applied.")
                return Result.success() // Defer without retry drain
            }

            LoggingManager.info("TelemetryWorker", "TELEMETRY_UPLOAD_START", "Starting batched non-urgent telemetry data upload.", "Charging: $isCharging, Battery: $batteryLevel%.")

            val syncManager = DeviceDataSyncManager(applicationContext)
            syncManager.performPeriodicSync()

            LoggingManager.info("TelemetryWorker", "TELEMETRY_UPLOAD_SUCCESS", "Batched telemetry upload completed successfully.", "Telemetry synchronized.")
            Result.success()
        } catch (e: Exception) {
            LoggingManager.critical("TelemetryWorker", "TELEMETRY_UPLOAD_FAILURE", "Telemetry upload failed: ${e.message}", "Retrying with exponential backoff.")
            Result.retry()
        }
    }

    companion object {
        fun schedulePeriodicUpload(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val uploadRequest = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "BatchedTelemetryUploadWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                uploadRequest
            )
        }
    }
}
