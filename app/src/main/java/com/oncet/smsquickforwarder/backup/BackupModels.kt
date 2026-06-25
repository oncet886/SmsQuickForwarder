package com.oncet.smsquickforwarder.backup

data class BackupPreview(
    val valid: Boolean,
    val schemaVersion: Int,
    val appVersionName: String,
    val exportedAt: String,
    val hasTargetPhone: Boolean,
    val ruleCount: Int,
    val unknownFieldCount: Int,
    val message: String
)

data class BackupImportPlan(
    val validRules: Int,
    val duplicateRules: Int,
    val invalidRules: Int
)

enum class RestoreMode {
    MERGE,
    REPLACE
}
