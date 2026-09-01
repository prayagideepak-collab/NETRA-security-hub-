package com.aistudio.netrasensorhub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppVersionDao {
    @Query("SELECT * FROM app_version WHERE id = 1")
    suspend fun getAppVersion(): AppVersionEntity?

    @Query("SELECT * FROM app_version WHERE id = 1")
    fun observeAppVersion(): Flow<AppVersionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(entity: AppVersionEntity)
}
