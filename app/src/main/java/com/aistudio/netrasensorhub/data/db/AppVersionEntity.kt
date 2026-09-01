package com.aistudio.netrasensorhub.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version")
data class AppVersionEntity(
    @PrimaryKey val id: Int = 1,
    val versionName: String,
    val versionCode: Int,
    val buildTag: String,
    val updatedTimestamp: Long = System.currentTimeMillis()
)
