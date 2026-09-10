package com.example.data.engine

import android.content.Context
import androidx.work.*
import com.example.util.LoggingManager
import java.util.concurrent.TimeUnit

class OfficialWarningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val manager = OfficialEmergencyWarningManager(applicationContext)
            manager.checkOfficialWarnings()
            Result.success()
        } catch (e: Exception) {
            LoggingManager.critical("OfficialWarningWorker", "WORKER_FAILURE", "Official warning check failed: ${e.message}", "Retrying later.")
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<OfficialWarningWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "OfficialEmergencyWarningPeriodicWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
