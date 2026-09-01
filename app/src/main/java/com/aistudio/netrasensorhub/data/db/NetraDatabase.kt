package com.aistudio.netrasensorhub.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppVersionEntity::class], version = 1, exportSchema = false)
abstract class NetraDatabase : RoomDatabase() {
    abstract fun appVersionDao(): AppVersionDao

    companion object {
        @Volatile
        private var INSTANCE: NetraDatabase? = null

        fun getDatabase(context: Context): NetraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetraDatabase::class.java,
                    "netra_sensor_hub.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
