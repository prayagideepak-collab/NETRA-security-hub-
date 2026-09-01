package com.example.nasre

/**
 * Base interface for all NASRE modules
 */
interface INasreModule {
    fun initialize()
    fun start()
    fun stop()
    fun getStatus(): String
}
