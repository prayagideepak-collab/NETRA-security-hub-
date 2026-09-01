package com.example.nasre

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.example.data.repository.NetraSafetyRepository

/**
 * Content provider for NASRE initialization.
 */
class NasreInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let { ctx ->
            val repository = NetraSafetyRepository(ctx)
            val nasre = NasreEngine.getInstance(
                ctx,
                repository.batteryManager,
                repository.sensorManager,
                null // repository.securityEngine is null in repo?
            )
            nasre.start()

            // Handle Persistence
            val persistenceManager = DataPersistenceManager(ctx)
            persistenceManager.initialize()

            // Log version update
            val prefs = ctx.getSharedPreferences("netra_prefs", Context.MODE_PRIVATE)
            val lastVersion = prefs.getString("last_version", "0.0")
            val currentVersion = "1.0" // BuildConfig.VERSION_NAME

            if (lastVersion != currentVersion) {
                // Log update event
                // ...
                prefs.edit().putString("last_version", currentVersion).apply()
            }
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
