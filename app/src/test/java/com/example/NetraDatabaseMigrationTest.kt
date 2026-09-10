package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.audit.ServiceStateAuditEntity
import com.example.data.audit.UnifiedEventEntity
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.db.SystemAuditEntity
import com.example.nasre.db.DiagnosticLogEntity
import com.example.nasre.db.HealthMonitorEntity
import com.example.nasre.db.ResourceOptimizerEntity
import com.example.nasre.db.RootCauseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetraDatabaseMigrationTest {

    private lateinit var context: Context

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NetraDatabase::class.java
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Primary integration test:
     * 1. Constructs an actual database with the exact v17 schema (including all 8 retained entities + 4 obsolete motion tables).
     * 2. Populates representative records into all retained tables and obsolete motion tables.
     * 3. Closes v17 and opens the database through Room's actual [Room.databaseBuilder] registering [NetraDatabase.MIGRATION_17_18].
     * 4. Allows Room itself to detect version 17, select MIGRATION_17_18, execute it, and validate the resulting v18 schema.
     * 5. Asserts that all retained records survived with full data fidelity (primary keys, columns, nullables).
     * 6. Asserts that the 4 obsolete Motion tables are completely dropped.
     * 7. Asserts that Room schema validation succeeded and all DAOs function on the upgraded v18 database.
     */
    @Test
    fun testRealRoomUpgrade_fromV17ToV18_usingDatabaseBuilder_validatesSchemaAndDataPreservation() {
        runBlocking {
            val dbName = "real-upgrade-test.db"
        context.deleteDatabase(dbName)

        // Step 1: Create v17 database using exact exported v17 schema
        val v17Db = helper.createDatabase(dbName, 17)

        // Step 2: Insert representative records into ALL 8 retained v17 tables
        v17Db.execSQL(
            """
            INSERT INTO safety_events (
                id, eventId, domain, lifecycleState, timestamp, startTime, lastUpdateTime,
                endTime, riskLevel, riskScore, eventType, title, description, peakValue,
                currentValue, thresholdValue, primarySensorValuesJson, aiRecommendation,
                isVerifiedHardwareEvent, moduleName, severity, aiConfidence, evidence,
                resolution, batteryPercent, deviceTempC, processingDurationMs,
                recoveryDurationMs, gpsLocation, announcementStatus
            ) VALUES (
                1, 'EVT-THERMAL-001', 'THERMAL', 'ACTIVE', 1700000000000, 1700000000000, 1700000001000,
                NULL, 'WARNING', 85, 'OVERHEAT', 'Device Overheat Detected', 'Internal temperature reached 43.5C', '43.5C',
                '42.0C', '40.0C', '{"batteryTemp": 42.0}', 'Close intensive background tasks',
                1, 'SafetyEngine', 'WARNING', 0.96, 'Hardware thermal probe',
                NULL, 78, 43.5, 12,
                0, '12.9716,77.5946', 'ANNOUNCED'
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO system_audits (
                id, timestamp, durationMs, totalServicesChecked, healthyServices,
                restartedServices, failedServices, unsupportedComponents,
                recoveryActionsPerformed, overallSystemHealthScore, servicesDetailsJson
            ) VALUES (
                1, 1700000005000, 150, 12, 11,
                1, 0, 0,
                'Restarted CoreSensorService', 97, '{"services": ["CoreSensorService"]}'
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO service_state_audit (
                id, serviceName, previousState, newState, timestamp,
                triggerSource, reason, status, startTime, endTime, durationMs
            ) VALUES (
                1, 'SentinelWatchdog', 'DISABLED', 'ENABLED', 1700000010000,
                'SystemRecovery', 'Self-healing trigger', 'SUCCESS', 1700000010000, 1700000010200, 200
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO unified_event_history (
                id, timestamp, category, severity, eventName,
                sourceModule, description, status, resolutionStatus,
                metadataJson, occurrences, totalDurationMs
            ) VALUES (
                1, 1700000015000, 'Safety', 'Warning', 'ThermalSpike',
                'ThermalEngine', 'Thermal threshold passed', 'OPEN', NULL,
                '{"sensor": "temp"}', 3, 600
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO health_monitor (
                id, timestamp, moduleName, status, memoryUsage,
                cpuUsage, threadCount, activeWorkers, healthScore,
                lastHeartbeat, failureCount
            ) VALUES (
                1, 1700000020000, 'CoreHealth', 'HEALTHY', 52428800,
                4.2, 6, 2, 99,
                1700000020000, 0
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO diagnostic_logs (
                id, timestamp, severity, module, event,
                description, recoveryAction, result, sessionId, buildVersion
            ) VALUES (
                1, 1700000025000, 'INFO', 'CoreHealth', 'BOOT_CHECK',
                'Core module startup diagnostic clean', NULL, 'SUCCESS', 'SESSION-V17-01', '1.0.0'
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO resource_optimizer (
                id, timestamp, cpu, ram, workerCount,
                optimizationApplied, beforeState, afterState, result
            ) VALUES (
                1, 1700000030000, 18.5, 62914560, 4,
                'ThreadPrune', 'RAM: 60MB', 'RAM: 45MB', 'OPTIMIZED'
            )
            """.trimIndent()
        )

        v17Db.execSQL(
            """
            INSERT INTO root_cause (
                id, timestamp, module, failureType, rootCause,
                threadDump, exception, memorySnapshot, cpuSnapshot,
                recommendedRecovery, recoveryExecuted, recoveryResult
            ) VALUES (
                1, 1700000035000, 'ThermalEngine', 'ThermalRunaway', 'High CPU during background sync',
                NULL, NULL, NULL, NULL,
                'Throttle background sync', 'Throttled background sync worker', 'SUCCESS'
            )
            """.trimIndent()
        )

        // Insert representative records into the 4 obsolete Motion tables
        v17Db.execSQL("INSERT INTO daily_motion_summary (id, date) VALUES (1, '2026-09-02')")
        v17Db.execSQL("INSERT INTO motion_events (id, type) VALUES (1, 'WALKING')")
        v17Db.execSQL("INSERT INTO motion_route_sessions (id, title) VALUES (1, 'Morning Commute')")
        v17Db.execSQL("INSERT INTO route_events (id, event) VALUES (1, 'WAYPOINT_REACHED')")

        // Step 3: Close the v17 SQLite database
        v17Db.close()

        // Step 4: Open and upgrade the database via Room's actual Room.databaseBuilder mechanism
        val upgradedDb = Room.databaseBuilder(
            context,
            NetraDatabase::class.java,
            dbName
        )
        .addMigrations(NetraDatabase.MIGRATION_17_18)
        .allowMainThreadQueries()
        .build()

        val sqliteDb = upgradedDb.openHelper.writableDatabase

        // Step 5: Verify version is now 18
        assertEquals(18, getUserVersion(sqliteDb))

        // Step 6: Verify identity_hash in room_master_table is updated to v18
        val identityHash = getIdentityHash(sqliteDb)
        assertNotNull(identityHash)
        assertEquals("719ea69f3bcdec01c593e58737ae4551", identityHash)

        // Step 7: Verify all 4 obsolete Motion tables are gone
        assertFalse("daily_motion_summary should be dropped", tableExists(sqliteDb, "daily_motion_summary"))
        assertFalse("motion_events should be dropped", tableExists(sqliteDb, "motion_events"))
        assertFalse("motion_route_sessions should be dropped", tableExists(sqliteDb, "motion_route_sessions"))
        assertFalse("route_events should be dropped", tableExists(sqliteDb, "route_events"))

        // Assert querying dropped tables throws SQLiteException
        try {
            sqliteDb.query("SELECT * FROM daily_motion_summary")
            fail("Querying dropped table daily_motion_summary should have thrown SQLiteException")
        } catch (expected: SQLiteException) {
            // Expected
        }

        // Step 8: Verify all 8 retained tables exist
        val expectedRetainedTables = listOf(
            "safety_events",
            "system_audits",
            "service_state_audit",
            "unified_event_history",
            "health_monitor",
            "diagnostic_logs",
            "resource_optimizer",
            "root_cause"
        )
        for (table in expectedRetainedTables) {
            assertTrue("Table $table must exist after migration", tableExists(sqliteDb, table))
        }

        // Step 9: Verify indexes on retained tables exist
        assertTrue("Index on safety_events.timestamp must exist", indexExists(sqliteDb, "index_safety_events_timestamp"))
        assertTrue("Index on safety_events.eventId must exist", indexExists(sqliteDb, "index_safety_events_eventId"))
        assertTrue("Index on safety_events.lifecycleState must exist", indexExists(sqliteDb, "index_safety_events_lifecycleState"))

        // Step 10: Verify data preservation in ALL retained tables via DAOs and queries

        // 1. safety_events
        val safetyEvents = upgradedDb.safetyEventDao().getAllEvents().first()
        assertEquals(1, safetyEvents.size)
        val safetyEvent = safetyEvents[0]
        assertEquals(1L, safetyEvent.id)
        assertEquals("EVT-THERMAL-001", safetyEvent.eventId)
        assertEquals("THERMAL", safetyEvent.domain)
        assertEquals("ACTIVE", safetyEvent.lifecycleState)
        assertEquals("WARNING", safetyEvent.riskLevel)
        assertEquals(85, safetyEvent.riskScore)
        assertEquals("Device Overheat Detected", safetyEvent.title)
        assertEquals("Internal temperature reached 43.5C", safetyEvent.description)
        assertEquals("43.5C", safetyEvent.peakValue)
        assertNull(safetyEvent.endTime)
        assertNull(safetyEvent.resolution)
        assertEquals(43.5f, safetyEvent.deviceTempC, 0.01f)
        assertEquals(78, safetyEvent.batteryPercent)
        assertEquals("12.9716,77.5946", safetyEvent.gpsLocation)
        assertEquals("ANNOUNCED", safetyEvent.announcementStatus)

        // 2. system_audits
        val audits = upgradedDb.systemAuditDao().getAllAudits().first()
        assertEquals(1, audits.size)
        val audit = audits[0]
        assertEquals(1L, audit.id)
        assertEquals(150L, audit.durationMs)
        assertEquals(12, audit.totalServicesChecked)
        assertEquals(11, audit.healthyServices)
        assertEquals(1, audit.restartedServices)
        assertEquals(0, audit.failedServices)
        assertEquals(97, audit.overallSystemHealthScore)
        assertEquals("Restarted CoreSensorService", audit.recoveryActionsPerformed)

        // 3. service_state_audit
        val serviceAudits = upgradedDb.serviceStateAuditDao().getAllAuditRecords().first()
        assertEquals(1, serviceAudits.size)
        val sAudit = serviceAudits[0]
        assertEquals(1L, sAudit.id)
        assertEquals("SentinelWatchdog", sAudit.serviceName)
        assertEquals("DISABLED", sAudit.previousState)
        assertEquals("ENABLED", sAudit.newState)
        assertEquals("SystemRecovery", sAudit.triggerSource)
        assertEquals("SUCCESS", sAudit.status)
        assertEquals(200L, sAudit.durationMs)

        // 4. unified_event_history
        val unifiedEvents = upgradedDb.unifiedEventDao().getEventsPaginated(10, 0).first()
        assertEquals(1, unifiedEvents.size)
        val uEvent = unifiedEvents[0]
        assertEquals(1L, uEvent.id)
        assertEquals("Safety", uEvent.category)
        assertEquals("Warning", uEvent.severity)
        assertEquals("ThermalSpike", uEvent.eventName)
        assertEquals("ThermalEngine", uEvent.sourceModule)
        assertEquals("OPEN", uEvent.status)
        assertNull(uEvent.resolutionStatus)
        assertEquals(3, uEvent.occurrences)
        assertEquals(600L, uEvent.totalDurationMs)

        // 5. health_monitor
        val healthLogs = upgradedDb.nasreDao().getRecentHealthLogs()
        assertEquals(1, healthLogs.size)
        val hLog = healthLogs[0]
        assertEquals(1L, hLog.id)
        assertEquals("CoreHealth", hLog.moduleName)
        assertEquals("HEALTHY", hLog.status)
        assertEquals(52428800L, hLog.memoryUsage)
        assertEquals(4.2, hLog.cpuUsage, 0.01)
        assertEquals(6, hLog.threadCount)
        assertEquals(99, hLog.healthScore)

        // 6. diagnostic_logs
        val diagLogs = upgradedDb.nasreDao().getRecentDiagnosticLogs()
        assertEquals(1, diagLogs.size)
        val dLog = diagLogs[0]
        assertEquals(1L, dLog.id)
        assertEquals("INFO", dLog.severity)
        assertEquals("CoreHealth", dLog.module)
        assertEquals("BOOT_CHECK", dLog.event)
        assertEquals("SUCCESS", dLog.result)
        assertNull(dLog.recoveryAction)
        assertEquals("SESSION-V17-01", dLog.sessionId)

        // 7. resource_optimizer
        val roCursor = sqliteDb.query("SELECT id, cpu, ram, workerCount, optimizationApplied, result FROM resource_optimizer WHERE id = 1")
        assertTrue(roCursor.moveToFirst())
        assertEquals(1L, roCursor.getLong(0))
        assertEquals(18.5, roCursor.getDouble(1), 0.01)
        assertEquals(62914560L, roCursor.getLong(2))
        assertEquals(4, roCursor.getInt(3))
        assertEquals("ThreadPrune", roCursor.getString(4))
        assertEquals("OPTIMIZED", roCursor.getString(5))
        roCursor.close()

        // 8. root_cause
        val rcCursor = sqliteDb.query("SELECT id, module, failureType, rootCause, recommendedRecovery, recoveryResult FROM root_cause WHERE id = 1")
        assertTrue(rcCursor.moveToFirst())
        assertEquals(1L, rcCursor.getLong(0))
        assertEquals("ThermalEngine", rcCursor.getString(1))
        assertEquals("ThermalRunaway", rcCursor.getString(2))
        assertEquals("High CPU during background sync", rcCursor.getString(3))
        assertEquals("Throttle background sync", rcCursor.getString(4))
        assertEquals("SUCCESS", rcCursor.getString(5))
        rcCursor.close()

        // Step 11: Verify new DAO operations function on upgraded v18 database
        val newSafetyEvent = SafetyEventEntity(
            eventId = "EVT-POST-MIGRATION-002",
            domain = "MAGNETIC",
            lifecycleState = "DETECTED",
            riskLevel = "SAFE",
            title = "Magnetic Field Safe",
            description = "Field strength normal"
        )
        val insertedSafetyId = upgradedDb.safetyEventDao().insertEvent(newSafetyEvent)
        assertTrue(insertedSafetyId > 0)
        val fetchedEvent = upgradedDb.safetyEventDao().getEventById(insertedSafetyId)
        assertNotNull(fetchedEvent)
        assertEquals("EVT-POST-MIGRATION-002", fetchedEvent?.eventId)

        val newAudit = SystemAuditEntity(
            durationMs = 90,
            totalServicesChecked = 8,
            healthyServices = 8,
            restartedServices = 0,
            failedServices = 0,
            unsupportedComponents = 0,
            recoveryActionsPerformed = "None",
            overallSystemHealthScore = 100,
            servicesDetailsJson = "{}"
        )
        val insertedAuditId = upgradedDb.systemAuditDao().insertAudit(newAudit)
        assertTrue(insertedAuditId > 0)

        val newServiceAudit = ServiceStateAuditEntity(
            serviceName = "NetraEngine",
            previousState = "Enabled",
            newState = "Enabled",
            timestamp = System.currentTimeMillis(),
            triggerSource = "PeriodicCheck",
            reason = "Health check",
            status = "Success"
        )
        upgradedDb.serviceStateAuditDao().insertAuditRecord(newServiceAudit)

        val newUnifiedEvent = UnifiedEventEntity(
            category = "Audit",
            severity = "Information",
            eventName = "PostMigrationCheck",
            sourceModule = "MigrationVerifier",
            description = "Post-migration verification complete",
            status = "Resolved"
        )
        val insertedUnifiedId = upgradedDb.unifiedEventDao().insertEvent(newUnifiedEvent)
        assertTrue(insertedUnifiedId > 0)

        val newHealth = HealthMonitorEntity(
            timestamp = System.currentTimeMillis(),
            moduleName = "PostMigrationModule",
            status = "HEALTHY",
            memoryUsage = 2048L,
            cpuUsage = 1.0,
            threadCount = 4,
            activeWorkers = 1,
            healthScore = 100,
            lastHeartbeat = System.currentTimeMillis(),
            failureCount = 0
        )
        val insertedHealthId = upgradedDb.nasreDao().insertHealthMonitor(newHealth)
        assertTrue(insertedHealthId > 0)

        val newResourceOptimizer = ResourceOptimizerEntity(
            timestamp = System.currentTimeMillis(),
            cpu = 5.0,
            ram = 1024L,
            workerCount = 2,
            optimizationApplied = "PostMigrationOptimizer",
            beforeState = "Normal",
            afterState = "Optimal",
            result = "SUCCESS"
        )
        val insertedRoId = upgradedDb.nasreDao().insertResourceOptimizer(newResourceOptimizer)
        assertTrue(insertedRoId > 0)

        val newRootCause = RootCauseEntity(
            timestamp = System.currentTimeMillis(),
            module = "PostMigrationDiag",
            failureType = "None",
            rootCause = "None",
            threadDump = null,
            exception = null,
            memorySnapshot = null,
            cpuSnapshot = null,
            recommendedRecovery = "None",
            recoveryExecuted = "None",
            recoveryResult = "NONE"
        )
        val insertedRcId = upgradedDb.nasreDao().insertRootCause(newRootCause)
        assertTrue(insertedRcId > 0)

        upgradedDb.close()
        context.deleteDatabase(dbName)
        }
    }

    /**
     * Test Room's MigrationTestHelper schema validation:
     * Exercises helper.runMigrationsAndValidate, which enforces Room's internal schema validator
     * against the exported v18 schema json.
     */
    @Test
    fun testMigrationTestHelper_validatesSchema17To18() {
        val dbName = "helper-schema-validation-test.db"
        context.deleteDatabase(dbName)

        val v17Db = helper.createDatabase(dbName, 17)
        v17Db.execSQL("INSERT INTO daily_motion_summary (id, date) VALUES (1, '2026-09-02')")
        v17Db.execSQL("INSERT INTO motion_events (id, type) VALUES (1, 'RUN')")
        v17Db.execSQL("INSERT INTO motion_route_sessions (id, title) VALUES (1, 'Evening Run')")
        v17Db.execSQL("INSERT INTO route_events (id, event) VALUES (1, 'FINISH')")
        v17Db.close()

        // runMigrationsAndValidate executes MIGRATION_17_18 and validates schema against 18.json
        val migratedDb = helper.runMigrationsAndValidate(
            dbName,
            18,
            true,
            NetraDatabase.MIGRATION_17_18
        )

        // Verify motion tables are dropped
        assertFalse(tableExists(migratedDb, "daily_motion_summary"))
        assertFalse(tableExists(migratedDb, "motion_events"))
        assertFalse(tableExists(migratedDb, "motion_route_sessions"))
        assertFalse(tableExists(migratedDb, "route_events"))

        // Verify all 8 retained tables exist
        val retained = listOf(
            "safety_events",
            "system_audits",
            "service_state_audit",
            "unified_event_history",
            "health_monitor",
            "diagnostic_logs",
            "resource_optimizer",
            "root_cause"
        )
        for (t in retained) {
            assertTrue("Retained table $t must exist in migrated schema", tableExists(migratedDb, t))
        }

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    /**
     * Fresh v18 database test:
     * Validates that a fresh v18 database opens cleanly, contains all 8 entities,
     * contains zero motion tables, and supports all DAO operations.
     */
    @Test
    fun testFreshV18Database_containsAllEightEntitiesAndNoMotionTables() {
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(context, NetraDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            val sqliteDb = db.openHelper.readableDatabase

            // Verify user version is 18
            assertEquals(18, getUserVersion(sqliteDb))

            // Verify all 8 entities exist
            val expectedTables = listOf(
                "safety_events",
                "system_audits",
                "service_state_audit",
                "unified_event_history",
                "health_monitor",
                "diagnostic_logs",
                "resource_optimizer",
                "root_cause"
            )
            for (table in expectedTables) {
                assertTrue("Fresh v18 DB must contain table: $table", tableExists(sqliteDb, table))
            }

            // Verify NO obsolete motion tables exist
            assertFalse("Fresh v18 DB must not contain daily_motion_summary", tableExists(sqliteDb, "daily_motion_summary"))
            assertFalse("Fresh v18 DB must not contain motion_events", tableExists(sqliteDb, "motion_events"))
            assertFalse("Fresh v18 DB must not contain motion_route_sessions", tableExists(sqliteDb, "motion_route_sessions"))
            assertFalse("Fresh v18 DB must not contain route_events", tableExists(sqliteDb, "route_events"))

            // Verify all DAOs work on fresh database
            val safetyEvent = SafetyEventEntity(
                eventId = "V18-FRESH-001",
                domain = "SYSTEM",
                title = "Fresh Boot Event",
                description = "Initialization test"
            )
            val id = db.safetyEventDao().insertEvent(safetyEvent)
            assertTrue(id > 0)
            val events = db.safetyEventDao().getAllEvents().first()
            assertEquals(1, events.size)
            assertEquals("V18-FRESH-001", events[0].eventId)

            val audit = SystemAuditEntity(
                durationMs = 50,
                totalServicesChecked = 5,
                healthyServices = 5,
                restartedServices = 0,
                failedServices = 0,
                unsupportedComponents = 0,
                recoveryActionsPerformed = "None",
                overallSystemHealthScore = 100,
                servicesDetailsJson = "{}"
            )
            val auditId = db.systemAuditDao().insertAudit(audit)
            assertTrue(auditId > 0)

            val serviceAudit = ServiceStateAuditEntity(
                serviceName = "InitService",
                previousState = "None",
                newState = "Active",
                timestamp = System.currentTimeMillis(),
                triggerSource = "Boot",
                reason = "Startup",
                status = "Success"
            )
            db.serviceStateAuditDao().insertAuditRecord(serviceAudit)

            val unified = UnifiedEventEntity(
                category = "System",
                severity = "Information",
                eventName = "FreshInstallReady",
                sourceModule = "BootLoader",
                description = "System initialized",
                status = "Active"
            )
            val unifiedId = db.unifiedEventDao().insertEvent(unified)
            assertTrue(unifiedId > 0)

            val healthLog = HealthMonitorEntity(
                timestamp = System.currentTimeMillis(),
                moduleName = "InitHealth",
                status = "HEALTHY",
                memoryUsage = 1024L,
                cpuUsage = 0.1,
                threadCount = 2,
                activeWorkers = 1,
                healthScore = 100,
                lastHeartbeat = System.currentTimeMillis(),
                failureCount = 0
            )
            val healthId = db.nasreDao().insertHealthMonitor(healthLog)
            assertTrue(healthId > 0)

            db.close()
        }
    }

    /**
     * Direct unit test:
     * Verifies that MIGRATION_17_18 executes DROP TABLE IF EXISTS cleanly and is idempotent.
     */
    @Test
    fun testDirectMigration17To18_isIdempotentAndSafe() {
        val dbName = "direct-migration-test.db"
        context.deleteDatabase(dbName)

        val db = helper.createDatabase(dbName, 17)
        assertTrue(tableExists(db, "daily_motion_summary"))
        assertTrue(tableExists(db, "motion_events"))

        // First execution
        NetraDatabase.MIGRATION_17_18.migrate(db)
        assertFalse(tableExists(db, "daily_motion_summary"))
        assertFalse(tableExists(db, "motion_events"))
        assertFalse(tableExists(db, "motion_route_sessions"))
        assertFalse(tableExists(db, "route_events"))

        // Second execution to verify idempotency (no exception thrown)
        NetraDatabase.MIGRATION_17_18.migrate(db)

        db.close()
        context.deleteDatabase(dbName)
    }

    private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    private fun indexExists(db: SupportSQLiteDatabase, indexName: String): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='index' AND name=?", arrayOf(indexName))
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    private fun getUserVersion(db: SupportSQLiteDatabase): Int {
        val cursor = db.query("PRAGMA user_version")
        val version = if (cursor.moveToFirst()) cursor.getInt(0) else -1
        cursor.close()
        return version
    }

    private fun getIdentityHash(db: SupportSQLiteDatabase): String? {
        val cursor = db.query("SELECT identity_hash FROM room_master_table WHERE id = 42 LIMIT 1")
        val hash = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return hash
    }
}
