package com.oncet.smsquickforwarder.health

enum class HealthSeverity {
    OK,
    WARNING,
    ACTION_REQUIRED
}

data class HealthCheckItem(
    val name: String,
    val severity: HealthSeverity,
    val summary: String,
    val actionLabel: String = ""
)

data class HealthReport(
    val overall: HealthSeverity,
    val items: List<HealthCheckItem>
) {
    val actionRequiredCount: Int = items.count { it.severity == HealthSeverity.ACTION_REQUIRED }
    val warningCount: Int = items.count { it.severity == HealthSeverity.WARNING }
}

object HealthRules {
    fun overall(items: List<HealthCheckItem>): HealthSeverity = when {
        items.any { it.severity == HealthSeverity.ACTION_REQUIRED } -> HealthSeverity.ACTION_REQUIRED
        items.any { it.severity == HealthSeverity.WARNING } -> HealthSeverity.WARNING
        else -> HealthSeverity.OK
    }
}
