package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.db.SystemAuditEntity
import com.example.data.audit.ServiceStateAuditEntity
import com.example.data.audit.UnifiedEventEntity
import com.example.nasre.db.HealthMonitorEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetraDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testMigration17To18_dropsMotionTablesAndPreservesAllUnrelatedTables() {
        val dbName = "test_migration_17_18.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v17 tables
                    // 1. Safety Events
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `safety_events` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `eventId` TEXT NOT NULL,
                            `domain` TEXT NOT NULL,
                            `lifecycleState` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `lastUpdateTime` INTEGER NOT NULL,
                            `endTime` INTEGER,
                            `riskLevel` TEXT NOT NULL,
                            `riskScore` INTEGER NOT NULL,
                            `eventType` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `peakValue` TEXT,
                            `currentValue` TEXT,
                            `thresholdValue` TEXT,
                            `primarySensorValuesJson` TEXT NOT NULL,
                            `aiRecommendation` TEXT NOT NULL,
                            `isVerifiedHardwareEvent` INTEGER NOT NULL,
                            `moduleName` TEXT NOT NULL,
                            `severity` TEXT NOT NULL,
                            `aiConfidence` REAL NOT NULL,
                            `evidence` TEXT NOT NULL,
                            `resolution` TEXT,
                            `batteryPercent` INTEGER NOT NULL,
                            `deviceTempC` REAL NOT NULL,
                            `processingDurationMs` INTEGER NOT NULL,
                            `recoveryDurationMs` INTEGER NOT NULL,
                            `gpsLocation` TEXT NOT NULL,
                            `announcementStatus` TEXT NOT NULL
                        )
                    """.trimIndent())

                    // 2. System Audits
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `system_audits` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `durationMs` INTEGER NOT NULL,
                            `totalServicesChecked` INTEGER NOT NULL,
                            `healthyServices` INTEGER NOT NULL,
                            `restartedServices` INTEGER NOT NULL,
                            `failedServices` INTEGER NOT NULL,
                            `unsupportedComponents` INTEGER NOT NULL,
                            `recoveryActionsPerformed` TEXT NOT NULL,
                            `overallSystemHealthScore` INTEGER NOT NULL,
                            `servicesDetailsJson` TEXT NOT NULL
                        )
                    """.trimIndent())

                    // 3. Service State Audit
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `service_state_audit` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `serviceName` TEXT NOT NULL,
                            `previousState` TEXT NOT NULL,
                            `newState` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `triggerSource` TEXT NOT NULL,
                            `reason` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `startTime` INTEGER,
                            `endTime` INTEGER,
                            `durationMs` INTEGER
                        )
                    """.trimIndent())

                    // 4. Unified Event History
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `unified_event_history` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `category` TEXT NOT NULL,
                            `severity` TEXT NOT NULL,
                            `eventName` TEXT NOT NULL,
                            `sourceModule` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `resolutionStatus` TEXT,
                            `metadataJson` TEXT,
                            `occurrences` INTEGER NOT NULL,
                            `totalDurationMs` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // 5. NASRE Health Monitor
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `health_monitor` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `moduleName` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `memoryUsage` INTEGER NOT NULL,
                            `cpuUsage` REAL NOT NULL,
                            `threadCount` INTEGER NOT NULL,
                            `activeWorkers` INTEGER NOT NULL,
                            `healthScore` INTEGER NOT NULL,
                            `lastHeartbeat` INTEGER NOT NULL,
                            `failureCount` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // 6. Old Motion Tables in v17
                    db.execSQL("CREATE TABLE IF NOT EXISTS `daily_motion_summary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `motion_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `motion_route_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `route_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event` TEXT NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val writableDb = helper.writableDatabase

        // Insert test data in v17
        writableDb.execSQL("INSERT INTO safety_events (eventId, domain, lifecycleState, timestamp, startTime, lastUpdateTime, riskLevel, riskScore, eventType, title, description, primarySensorValuesJson, aiRecommendation, isVerifiedHardwareEvent, moduleName, severity, aiConfidence, evidence, batteryPercent, deviceTempC, processingDurationMs, recoveryDurationMs, gpsLocation, announcementStatus) VALUES ('TEST-001', 'THERMAL', 'RESOLVED', 1000, 1000, 1000, 'SAFE', 0, 'THERMAL_SPIKE', 'Test Spike', 'Normalizing', '{}', 'Cool down', 1, 'SafetyEngine', 'NORMAL', 0.99, 'None', 85, 32.5, 10, 0, 'Unavailable', 'N/A')")
        writableDb.execSQL("INSERT INTO system_audits (timestamp, durationMs, totalServicesChecked, healthyServices, restartedServices, failedServices, unsupportedComponents, recoveryActionsPerformed, overallSystemHealthScore, servicesDetailsJson) VALUES (2000, 50, 10, 10, 0, 0, 0, 'None', 100, '{}')")
        writableDb.execSQL("INSERT INTO daily_motion_summary (date) VALUES ('2026-09-02')")
        writableDb.execSQL("INSERT INTO motion_events (type) VALUES ('WALK')")

        // Perform Migration 17 -> 18
        NetraDatabase.MIGRATION_17_18.migrate(writableDb)

        // Verify Motion tables are dropped
        assertFalse(tableExists(writableDb, "daily_motion_summary"))
        assertFalse(tableExists(writableDb, "motion_events"))
        assertFalse(tableExists(writableDb, "motion_route_sessions"))
        assertFalse(tableExists(writableDb, "route_events"))

        // Verify Safety & System Audit tables still exist and contain data
        assertTrue(tableExists(writableDb, "safety_events"))
        assertTrue(tableExists(writableDb, "system_audits"))
        assertTrue(tableExists(writableDb, "service_state_audit"))
        assertTrue(tableExists(writableDb, "unified_event_history"))
        assertTrue(tableExists(writableDb, "health_monitor"))

        val safetyCursor = writableDb.query("SELECT eventId, title, severity FROM safety_events WHERE eventId = 'TEST-001'")
        assertTrue(safetyCursor.moveToFirst())
        assertEquals("TEST-001", safetyCursor.getString(0))
        assertEquals("Test Spike", safetyCursor.getString(1))
        assertEquals("NORMAL", safetyCursor.getString(2))
        safetyCursor.close()

        val auditCursor = writableDb.query("SELECT overallSystemHealthScore FROM system_audits WHERE timestamp = 2000")
        assertTrue(auditCursor.moveToFirst())
        assertEquals(100, auditCursor.getInt(0))
        auditCursor.close()

        writableDb.close()
        helper.close()
    }

    @Test
    fun testFreshV18Database_allowsAllDaoOperations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, NetraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val safetyDao = db.safetyEventDao()
        val auditDao = db.systemAuditDao()
        val serviceAuditDao = db.serviceStateAuditDao()
        val unifiedDao = db.unifiedEventDao()
        val nasreDao = db.nasreDao()

        // Test Safety Event DAO
        val event = SafetyEventEntity(
            eventId = "V18-TEST-01",
            domain = "MAGNETIC",
            lifecycleState = "ACTIVE",
            riskLevel = "WARNING",
            eventType = "ANOMALY",
            title = "Magnetic Spike",
            description = "High magnetic field detected"
        )
        safetyDao.insertEvent(event)
        val fetchedEvents = safetyDao.getAllEvents().first()
        assertEquals(1, fetchedEvents.size)
        assertEquals("V18-TEST-01", fetchedEvents[0].eventId)

        // Test System Audit DAO
        val audit = SystemAuditEntity(
            durationMs = 120,
            totalServicesChecked = 15,
            healthyServices = 15,
            restartedServices = 0,
            failedServices = 0,
            unsupportedComponents = 0,
            recoveryActionsPerformed = "Optimal",
            overallSystemHealthScore = 98,
            servicesDetailsJson = "{}"
        )
        auditDao.insertAudit(audit)
        val audits = auditDao.getAllAudits().first()
        assertEquals(1, audits.size)
        assertEquals(98, audits[0].overallSystemHealthScore)

        // Test Service State Audit DAO
        val serviceAudit = ServiceStateAuditEntity(
            serviceName = "SensorService",
            previousState = "Disabled",
            newState = "Enabled",
            timestamp = System.currentTimeMillis(),
            triggerSource = "User",
            reason = "Manual start",
            status = "Success"
        )
        serviceAuditDao.insertAuditRecord(serviceAudit)
        val serviceAudits = serviceAuditDao.getAllAuditRecords().first()
        assertEquals(1, serviceAudits.size)

        // Test Unified Event DAO
        val unified = UnifiedEventEntity(
            category = "Safety",
            severity = "Information",
            eventName = "SelfAuditPassed",
            sourceModule = "NetraSafetyEngine",
            description = "All subsystems optimal",
            status = "Active"
        )
        unifiedDao.insertEvent(unified)
        val unifiedEvents = unifiedDao.getEventsPaginated(10, 0).first()
        assertEquals(1, unifiedEvents.size)

        // Test NASRE DAO
        val healthLog = HealthMonitorEntity(
            timestamp = System.currentTimeMillis(),
            moduleName = "Watchdog",
            status = "HEALTHY",
            memoryUsage = 1024L,
            cpuUsage = 0.5,
            threadCount = 4,
            activeWorkers = 1,
            healthScore = 100,
            lastHeartbeat = System.currentTimeMillis(),
            failureCount = 0
        )
        nasreDao.insertHealthMonitor(healthLog)
        val healthLogs = nasreDao.getRecentHealthLogs()
        assertEquals(1, healthLogs.size)

        db.close()
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }
}
