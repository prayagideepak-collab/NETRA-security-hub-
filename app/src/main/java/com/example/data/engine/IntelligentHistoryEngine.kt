package com.example.data.engine

import com.example.data.audit.UnifiedEventDao
import com.example.data.audit.UnifiedEventEntity

/**
 * Intelligent Safety Status Engine (ISSE)
 * 
 * Evaluates the health and status of all monitored safety modules.
 */
object IntelligentHistoryEngine {

    private lateinit var unifiedEventDao: UnifiedEventDao

    fun initialize(dao: UnifiedEventDao) {
        unifiedEventDao = dao
    }

    suspend fun logEvent(
        category: String,
        severity: String,
        eventName: String,
        sourceModule: String,
        description: String,
        status: String
    ) {
        if (!::unifiedEventDao.isInitialized) return
        
        val existing = unifiedEventDao.findRecentDuplicate(eventName, sourceModule, status)
        
        if (existing != null && (System.currentTimeMillis() - existing.timestamp) < 60000) {
            // Deduplicate: If same event within 1 minute, merge
            unifiedEventDao.updateEventDeduplication(existing.id, System.currentTimeMillis() - existing.timestamp)
        } else {
            // Log as new
            unifiedEventDao.insertEvent(
                UnifiedEventEntity(
                    category = category,
                    severity = severity,
                    eventName = eventName,
                    sourceModule = sourceModule,
                    description = description,
                    status = status
                )
            )
        }
    }
}
