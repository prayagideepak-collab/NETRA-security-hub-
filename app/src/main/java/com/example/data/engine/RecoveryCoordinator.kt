package com.example.data.engine

import java.util.concurrent.ConcurrentHashMap

object RecoveryCoordinator {
    private val recoveryLocks = ConcurrentHashMap<String, Boolean>()

    fun tryLock(moduleName: String): Boolean {
        if (recoveryLocks[moduleName] == true) return false
        recoveryLocks[moduleName] = true
        return true
    }

    fun releaseLock(moduleName: String) {
        recoveryLocks[moduleName] = false
    }
}
