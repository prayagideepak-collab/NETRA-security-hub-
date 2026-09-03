package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.audit.ServiceStateAuditDao
import com.example.data.audit.ServiceStateAuditEntity
import com.example.data.audit.UnifiedEventDao
import com.example.data.audit.UnifiedEventEntity
import com.example.data.db.SafetyEventDao
import com.example.data.db.SafetyEventEntity
import com.example.data.db.SystemAuditDao
import com.example.data.db.SystemAuditEntity
import com.example.nasre.db.DiagnosticLogEntity
import com.example.nasre.db.HealthMonitorEntity
import com.example.nasre.db.NasreDao
import com.example.nasre.db.ResourceOptimizerEntity
import com.example.nasre.db.RootCauseEntity

@Database(
    entities = [
        SafetyEventEntity::class, 
        SystemAuditEntity::class,
        ServiceStateAuditEntity::class,
        UnifiedEventEntity::class,
        HealthMonitorEntity::class,
        DiagnosticLogEntity::class,
        ResourceOptimizerEntity::class,
        RootCauseEntity::class
    ], 
    version = 18, 
    exportSchema = false
)
abstract class NetraDatabase : RoomDatabase() {
    abstract fun safetyEventDao(): SafetyEventDao
    abstract fun systemAuditDao(): SystemAuditDao
    abstract fun serviceStateAuditDao(): ServiceStateAuditDao
    abstract fun unifiedEventDao(): UnifiedEventDao
    abstract fun nasreDao(): NasreDao

    companion object {
        @Volatile
        private var INSTANCE: NetraDatabase? = null

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop only the obsolete Motion-related tables intentionally removed in v18
                db.execSQL("DROP TABLE IF EXISTS daily_motion_summary")
                db.execSQL("DROP TABLE IF EXISTS motion_events")
                db.execSQL("DROP TABLE IF EXISTS motion_route_sessions")
                db.execSQL("DROP TABLE IF EXISTS route_events")
            }
        }

        fun getInstance(context: Context): NetraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetraDatabase::class.java,
                    "netra_safety_db"
                )
                .addMigrations(MIGRATION_17_18)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
