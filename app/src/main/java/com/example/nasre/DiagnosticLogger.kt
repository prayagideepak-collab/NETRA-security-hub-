package com.example.nasre

import android.content.Context
import com.example.nasre.db.DiagnosticLogEntity
import com.example.nasre.db.NasreDao
import com.example.data.db.NetraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Internal Diagnostic Logger
 */
class DiagnosticLogger(
    private val context: Context
) : INasreModule {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nasreDao: NasreDao = NetraDatabase.getInstance(context).nasreDao()

    override fun initialize() {}

    override fun start() {}

    override fun stop() {}

    override fun getStatus(): String = "Active"

    fun logEvent(severity: String, module: String, event: String, description: String, recoveryAction: String? = null, result: String? = null) {
        scope.launch {
            nasreDao.insertDiagnosticLog(
                DiagnosticLogEntity(
                    timestamp = System.currentTimeMillis(),
                    severity = severity,
                    module = module,
                    event = event,
                    description = description,
                    recoveryAction = recoveryAction,
                    result = result,
                    sessionId = "session_1", // Needs session tracking
                    buildVersion = "1.0"
                )
            )
        }
    }
}
