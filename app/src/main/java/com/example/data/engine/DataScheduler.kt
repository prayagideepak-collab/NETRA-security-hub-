package com.example.data.engine

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class DataScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    // Priority Matrix:
    // P0: Thermal/Charging/Integrity
    // P1: Activity
    // P2: Background Import
    // P3: Analytics

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "HybridDataSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    fun triggerImmediateSync() {
        val immediateRequest = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .build()
        workManager.enqueueUniqueWork(
            "ImmediateSync",
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }
}
