package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbePayloadParserTest {

    @Test
    fun `locates json body amid shell warnings`() {
        val raw = """
            WARNING: linker: app_process: Unused DT entry
            {"apps":[{"pkg":"com.a","label":"A"}]}
        """.trimIndent()
        val result = ProbePayloadParser.parse(raw)
        assertTrue(result is ProbePayloadParser.Result.Success)
        val apps = (result as ProbePayloadParser.Result.Success).apps
        assertEquals(1, apps.size)
        assertEquals("com.a", apps.first().packageName)
        assertEquals("A", apps.first().label)
    }

    @Test
    fun `decodes icon bytes`() {
        val raw = """{"apps":[{"pkg":"com.a","label":"A","iconBase64":"AAECAw=="}]}"""
        val result = ProbePayloadParser.parse(raw) as ProbePayloadParser.Result.Success
        assertEquals(listOf<Byte>(0, 1, 2, 3), result.iconBytes["com.a"]?.toList())
    }

    @Test
    fun `icon suffix follows the format the probe reported`() {
        val png = ProbePayloadParser.parse(
            """{"apps":[{"pkg":"com.a"}],"iconFmt":"png"}""",
        ) as ProbePayloadParser.Result.Success
        assertEquals(".png", png.iconSuffix)

        val webp = ProbePayloadParser.parse(
            """{"apps":[{"pkg":"com.a"}],"iconFmt":"webp"}""",
        ) as ProbePayloadParser.Result.Success
        assertEquals(".webp", webp.iconSuffix)
    }

    @Test
    fun `missing icon format falls back to webp`() {
        val result = ProbePayloadParser.parse("""{"apps":[{"pkg":"com.a"}]}""")
            as ProbePayloadParser.Result.Success
        assertEquals(".webp", result.iconSuffix)
    }

    @Test
    fun `reported probe error becomes failure with the message`() {
        val raw = """{"error":"Permission denial"}"""
        val result = ProbePayloadParser.parse(raw)
        assertEquals(ProbePayloadParser.Result.Failure("Permission denial"), result)
    }

    @Test
    fun `empty app list is a failure`() {
        val result = ProbePayloadParser.parse("""{"apps":[]}""")
        assertTrue(result is ProbePayloadParser.Result.Failure)
    }

    @Test
    fun `output without json reports the first line`() {
        val result = ProbePayloadParser.parse("Killed\n")
        assertEquals(ProbePayloadParser.Result.Failure("Killed"), result)
    }

    @Test
    fun `enabled and system flags map to the domain model`() {
        val raw = """{"apps":[{"pkg":"com.s","label":"S","ver":"2.0","size":2048,"sys":true,"enabled":false}]}"""
        val app = (ProbePayloadParser.parse(raw) as ProbePayloadParser.Result.Success).apps.first()
        assertEquals("2.0", app.versionName)
        assertEquals(2048L, app.apkSizeBytes)
        assertEquals(true, app.isSystem)
        assertEquals(false, app.isEnabled)
    }

    /**
     * 截断的报文不能把解析器拼进去的 JSON 原文（图标 base64）透传到界面。
     * 实测链路中途断开时输出只有 98KB，而完整清单约 1.4MB。
     */
    @Test
    fun `truncated payload reports a readable reason instead of raw json`() {
        // 结尾停在 base64 中间，没有任何闭合括号——与实测截断形态一致
        val truncated = """{"apps":[{"pkg":"com.a","label":"A","iconBase64":"AAAA"""
        val result = ProbePayloadParser.parse(truncated)
        assertTrue(result is ProbePayloadParser.Result.Failure)
        val reason = (result as ProbePayloadParser.Result.Failure).reason
        assertTrue("must not echo the json body, got: $reason", !reason.contains("iconBase64"))
        assertTrue("must not echo the json body, got: $reason", !reason.contains("AAAA"))
        assertTrue("must name the truncation, got: $reason", reason.contains("truncated"))
    }

    /**
     * 断点落在某个 app 对象闭合之后、数组未闭合时，宽松解析不抛错而是得到空清单。
     * 这条路径没有异常可看，仍须报「截断」。
     */
    @Test
    fun `truncation that yields an empty list is still reported as truncation`() {
        // 一个完整的 app 对象，但外层数组与根对象都没闭合
        val truncated = """{"apps":[{"pkg":"com.a","label":"A"}"""
        val result = ProbePayloadParser.parse(truncated)
        assertTrue(result is ProbePayloadParser.Result.Failure)
        val reason = (result as ProbePayloadParser.Result.Failure).reason
        assertTrue("must name the truncation, got: $reason", reason.contains("truncated"))
    }

    /** 真正完整的空清单仍报「没有应用」，不能被截断判定误伤。 */
    @Test
    fun `a complete empty list is not mistaken for truncation`() {
        val result = ProbePayloadParser.parse("""{"apps":[]}""")
        assertTrue(result is ProbePayloadParser.Result.Failure)
        val reason = (result as ProbePayloadParser.Result.Failure).reason
        assertTrue("must not claim truncation, got: $reason", !reason.contains("truncated"))
    }

    /**
     * 三处截断判定产出的串必须都能被 [ProbePayloadParser.truncatedChars] 反解。
     * UI 层靠它取字符数做本地化——任一处格式漂移都会静默退化成透传英文。
     */
    @Test
    fun `every truncation source is decodable by truncatedChars`() {
        val bodies = listOf(
            // describeMissingBody：有 { 无闭合 }
            """{"apps":[{"pkg":"com.a"""",
            // describeParseFailure：解析抛 EOF
            """{"apps":[{"pkg":"com.a","iconBase64":"AAAA"""",
            // describeEmptyApps：宽松解析成功但空清单，括号未配平
            """{"apps":[{"pkg":"com.a","label":"A"}""",
        )
        bodies.forEach { body ->
            val result = ProbePayloadParser.parse(body)
            assertTrue("expected failure for: $body", result is ProbePayloadParser.Result.Failure)
            val reason = (result as ProbePayloadParser.Result.Failure).reason
            val chars = ProbePayloadParser.truncatedChars(reason)
            assertTrue("must be decodable as truncation, got: $reason", chars != null)
            assertTrue("chars must be positive, got: $chars", chars!! > 0)
        }
    }

    /** 非截断串不能被误认成截断，否则会把设备错误显示成「清单不完整」。 */
    @Test
    fun `non truncation reasons are not decodable`() {
        assertEquals(null, ProbePayloadParser.truncatedChars("probe returned no apps"))
        assertEquals(null, ProbePayloadParser.truncatedChars("Permission denial"))
        assertEquals(null, ProbePayloadParser.truncatedChars(""))
    }
}
