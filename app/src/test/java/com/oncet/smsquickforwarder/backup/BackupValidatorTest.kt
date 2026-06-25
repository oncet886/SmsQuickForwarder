package com.oncet.smsquickforwarder.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupValidatorTest {
    @Test
    fun acceptsValidBackupAndIgnoresUnknownFields() {
        val preview = BackupValidator.preview(validBackup(extra = """"futureField": true,"""))
        assertTrue(preview.valid)
        assertEquals(1, preview.unknownFieldCount)
        assertEquals(1, preview.ruleCount)
    }

    @Test
    fun rejectsInvalidJsonAndUnsupportedSchema() {
        assertFalse(BackupValidator.preview("{").valid)
        assertFalse(BackupValidator.preview(validBackup(schema = 99)).valid)
    }

    @Test
    fun backupDoesNotContainPrivateLogData() {
        assertTrue(BackupValidator.excludesPrivateData(validBackup()))
        assertFalse(BackupValidator.excludesPrivateData("""{"fullBody":"secret"}"""))
    }

    private fun validBackup(schema: Int = 1, extra: String = "") = """
        {
          $extra
          "schemaVersion": $schema,
          "appVersionName": "0.1.9",
          "appVersionCode": 10,
          "exportedAt": "2026-06-25T12:00:00-07:00",
          "settings": {"targetPhone":"+16025550108"},
          "rules": [
            {"id":"1","name":"Code","type":"INCLUDE","field":"BODY","matchMode":"CONTAINS","pattern":"code"}
          ]
        }
    """.trimIndent()
}
