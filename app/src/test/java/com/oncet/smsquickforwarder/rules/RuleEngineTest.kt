package com.oncet.smsquickforwarder.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    @Test fun allModeNoRulesForwards() {
        val result = eval(ForwardMode.ALL, emptyList())
        assertTrue(result.shouldForward)
        assertEquals("forwarded_all_mode", result.decision)
    }

    @Test fun matchOnlyNoRulesSkips() {
        val result = eval(ForwardMode.MATCH_ONLY, emptyList())
        assertFalse(result.shouldForward)
        assertEquals("skipped_no_include_match", result.decision)
    }

    @Test fun includeMatchForwards() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Code", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code")))
        assertTrue(result.shouldForward)
        assertEquals("forwarded_rule_match", result.decision)
    }

    @Test fun includeMissSkips() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Bank", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "bank")))
        assertFalse(result.shouldForward)
        assertEquals("skipped_no_include_match", result.decision)
    }

    @Test fun excludeMatchSkips() {
        val result = eval(ForwardMode.ALL, listOf(rule("Ads", RuleType.EXCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "unsubscribe")))
        assertFalse(result.shouldForward)
        assertEquals("skipped_rule_exclude", result.decision)
    }

    @Test fun excludeBeatsInclude() {
        val rules = listOf(
            rule("Code", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code", priority = 2),
            rule("Ads", RuleType.EXCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "unsubscribe", priority = 1)
        )
        val result = eval(ForwardMode.MATCH_ONLY, rules, body = "Your code is 123456. STOP to unsubscribe")
        assertFalse(result.shouldForward)
        assertEquals("skipped_rule_exclude", result.decision)
    }

    @Test fun senderContainsMatchesNormalizedPhone() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Sender", RuleType.INCLUDE, RuleField.SENDER, RuleMatchMode.CONTAINS, "555010")), sender = "+1 555-010-6666")
        assertTrue(result.shouldForward)
    }

    @Test fun senderEqualsAllowsLastTenDigits() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Sender", RuleType.INCLUDE, RuleField.SENDER, RuleMatchMode.EQUALS, "5550106666")), sender = "+1 (555) 010-6666")
        assertTrue(result.shouldForward)
    }

    @Test fun bodyContainsIgnoresCase() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Code", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "verification code")), body = "Your Verification Code is 123456")
        assertTrue(result.shouldForward)
    }

    @Test fun bodyStartsWithMatches() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Starts", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.STARTS_WITH, "your")), body = "Your package arrived")
        assertTrue(result.shouldForward)
    }

    @Test fun regexMatches() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Regex", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.REGEX, "\\d{6}")))
        assertTrue(result.shouldForward)
    }

    @Test fun invalidRegexNotValid() {
        assertFalse(RuleEngine.isRegexValid("["))
    }

    @Test fun disabledRuleIgnored() {
        val result = eval(ForwardMode.MATCH_ONLY, listOf(rule("Code", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code", enabled = false)))
        assertFalse(result.shouldForward)
    }

    @Test fun prioritySelectsPrimary() {
        val rules = listOf(
            rule("Low", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code", priority = 10),
            rule("High", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code", priority = 1)
        )
        val result = eval(ForwardMode.MATCH_ONLY, rules)
        assertEquals("High", result.primaryRule?.name)
    }

    @Test fun loopGuardBeatsUserRules() {
        val result = RuleEngine.evaluate(
            sender = "+15550106666",
            body = "Your code is 123456",
            targetPhone = "5550106666",
            mode = ForwardMode.MATCH_ONLY,
            rules = listOf(rule("Code", RuleType.INCLUDE, RuleField.BODY, RuleMatchMode.CONTAINS, "code")),
            applyLoopGuard = true
        )
        assertFalse(result.shouldForward)
        assertEquals("skipped_from_target", result.decision)
    }

    @Test fun upgradeDefaultAllModeCompatible() {
        val result = RuleEngine.evaluate("111", "ordinary sms", "", ForwardMode.ALL, emptyList())
        assertTrue(result.shouldForward)
    }

    private fun eval(
        mode: ForwardMode,
        rules: List<SmsRule>,
        sender: String = "+15550106666",
        body: String = "Your code is 123456. STOP to unsubscribe"
    ): RuleEvaluation = RuleEngine.evaluate(sender, body, "+15550109999", mode, rules, applyLoopGuard = true)

    private fun rule(
        name: String,
        type: RuleType,
        field: RuleField,
        matchMode: RuleMatchMode,
        pattern: String,
        enabled: Boolean = true,
        priority: Int = 1
    ): SmsRule = SmsRule(
        id = name,
        name = name,
        enabled = enabled,
        type = type,
        field = field,
        matchMode = matchMode,
        pattern = pattern,
        caseSensitive = false,
        priority = priority,
        createdAt = 1L,
        updatedAt = 1L
    )
}
