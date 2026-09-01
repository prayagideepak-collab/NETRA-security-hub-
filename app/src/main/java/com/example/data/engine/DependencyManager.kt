package com.example.data.engine

import java.util.concurrent.ConcurrentHashMap

object DependencyManager {
    private val dependencies = ConcurrentHashMap<String, List<String>>()

    fun addDependency(moduleName: String, dependsOn: List<String>) {
        dependencies[moduleName] = dependsOn
    }

    fun getDependencies(moduleName: String): List<String> = dependencies[moduleName] ?: emptyList()
}
