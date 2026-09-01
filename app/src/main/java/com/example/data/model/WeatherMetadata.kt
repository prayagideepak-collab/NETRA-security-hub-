package com.example.data.model

data class WeatherMetadata(
    val lastSuccessfulUpdateTime: Long,
    val expectedNextUpdateTime: Long,
    val weatherSource: String,
    val weatherAge: Long,
    val updateInterval: Long
)
