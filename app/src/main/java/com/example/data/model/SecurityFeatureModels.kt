package com.example.data.model

enum class FeatureCategory {
    A, // Public API available (one-tap)
    B, // Intent only (open settings)
    C  // Status only (guidance)
}

enum class FeatureStatus {
    ENABLED,
    DISABLED,
    NOT_SUPPORTED,
    UNKNOWN
}

data class SecurityFeature(
    val id: String,
    val name: String,
    val category: FeatureCategory,
    val isMandatory: Boolean,
    val scoreWeight: Int,
    var status: FeatureStatus = FeatureStatus.UNKNOWN,
    val description: String = ""
)
