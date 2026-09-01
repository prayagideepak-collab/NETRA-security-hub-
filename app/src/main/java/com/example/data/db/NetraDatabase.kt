package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.audit.ServiceStateAuditDao
import com.example.data.audit.ServiceStateAuditEntity
import com.example.data.audit.UnifiedEventDao
import com.example.data.audit.UnifiedEventEntity
import com.example.data.db.SafetyEventDao
import com.example.data.db.SafetyEventEntity
import com.example.data.db.SystemAuditDao
import com.example.data.db.SystemAuditEntity
import com.example.data.db.DailyMotionSummaryEntity
import com.example.data.db.MotionEventEntity
import com.example.data.db.MotionDao
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
        RootCauseEntity::class,
        DailyMotionSummaryEntity::class,
        MotionEventEntity::class
    ], 
    version = 13, 
    exportSchema = false
)
abstract class NetraDatabase : RoomDatabase() {
    abstract fun safetyEventDao(): SafetyEventDao
    abstract fun systemAuditDao(): SystemAuditDao
    abstract fun serviceStateAuditDao(): ServiceStateAuditDao
    abstract fun unifiedEventDao(): UnifiedEventDao
    abstract fun nasreDao(): NasreDao
    abstract fun motionDao(): MotionDao

    companion object {
        @Volatile
        private var INSTANCE: NetraDatabase? = null

        fun getInstance(context: Context): NetraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetraDatabase::class.java,
                    "netra_safety_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
