package com.oncet.smsquickforwarder.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun draftAndPrereleaseAreIgnored() {
        assertTrue(UpdateChecker.parseReleaseJson(releaseJson(draft = true), "0.1.7") is UpdateCheckResult.UpToDate)
        assertTrue(UpdateChecker.parseReleaseJson(releaseJson(prerelease = true), "0.1.7") is UpdateCheckResult.UpToDate)
    }

    @Test
    fun ignoredVersionDoesNotPromptButNewerVersionDoes() {
        assertTrue(UpdateChecker.parseReleaseJson(releaseJson(tag = "v0.1.8"), "0.1.7", "0.1.8") is UpdateCheckResult.UpToDate)
        assertTrue(UpdateChecker.parseReleaseJson(releaseJson(tag = "v0.1.9"), "0.1.7", "0.1.8") is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun missingApkStillShowsRelease() {
        val result = UpdateChecker.parseReleaseJson(releaseJson(includeAssets = false), "0.1.7")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val info = (result as UpdateCheckResult.UpdateAvailable).info
        assertEquals("0.1.8", info.latestVersionName)
        assertEquals(null, info.apkName)
    }

    @Test
    fun rejectsUnexpectedReleaseUrl() {
        val result = UpdateChecker.parseReleaseJson(releaseJson(htmlUrl = "https://example.com/release"), "0.1.7")
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun parsesApkAndChecksumAssets() {
        val result = UpdateChecker.parseReleaseJson(releaseJson(), "0.1.7")
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val info = (result as UpdateCheckResult.UpdateAvailable).info
        assertEquals("SmsQuickForwarder-v0.1.8-9-release.apk", info.apkName)
        assertNotNull(info.sha256Name)
    }

    private fun releaseJson(
        tag: String = "v0.1.8",
        draft: Boolean = false,
        prerelease: Boolean = false,
        htmlUrl: String = "https://github.com/oncet886/SmsQuickForwarder/releases/tag/$tag",
        includeAssets: Boolean = true
    ): String {
        val assets = if (includeAssets) {
            """
            [
              {
                "name": "SmsQuickForwarder-v0.1.8-9-release.apk",
                "browser_download_url": "https://github.com/oncet886/SmsQuickForwarder/releases/download/v0.1.8/SmsQuickForwarder-v0.1.8-9-release.apk",
                "size": 12345
              },
              {
                "name": "SmsQuickForwarder-v0.1.8-9-release.apk.sha256",
                "browser_download_url": "https://github.com/oncet886/SmsQuickForwarder/releases/download/v0.1.8/SmsQuickForwarder-v0.1.8-9-release.apk.sha256",
                "size": 100
              }
            ]
            """.trimIndent()
        } else {
            "[]"
        }
        return """
        {
          "tag_name": "$tag",
          "name": "SmsQuickForwarder $tag",
          "body": "Release notes",
          "html_url": "$htmlUrl",
          "draft": $draft,
          "prerelease": $prerelease,
          "published_at": "2026-06-25T00:00:00Z",
          "assets": $assets
        }
        """.trimIndent()
    }
}
