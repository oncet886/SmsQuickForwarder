package com.oncet.smsquickforwarder.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthRulesTest {
    @Test
    fun overallStatusUsesWorstSeverity() {
        assertEquals(HealthSeverity.OK, HealthRules.overall(listOf(HealthCheckItem("ok", HealthSeverity.OK, ""))))
        assertEquals(HealthSeverity.WARNING, HealthRules.overall(listOf(HealthCheckItem("warn", HealthSeverity.WARNING, ""))))
        assertEquals(
            HealthSeverity.ACTION_REQUIRED,
            HealthRules.overall(listOf(HealthCheckItem("warn", HealthSeverity.WARNING, ""), HealthCheckItem("bad", HealthSeverity.ACTION_REQUIRED, "")))
        )
    }
}
