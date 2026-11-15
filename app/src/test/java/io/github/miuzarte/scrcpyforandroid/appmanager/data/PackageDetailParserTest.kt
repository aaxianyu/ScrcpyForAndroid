package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `dumpsys package <pkg>` 的解析口径。
 *
 * 样例按 Android 单包 dumpsys 输出裁剪：包段逐行 4 空格缩进，`User N:` 子段里的 `firstInstallTime` 再缩进 6 空格，
 * 包段之后是第 0 列起的顶层小节（`Queries:` 里还会再出现一个 `User 0:`）。
 */
class PackageDetailParserTest {

    @Test
    fun `a normal package block fills every field`() {
        val parsed = PackageDetailParser.parse(SAMPLE, "com.example.app")

        assertTrue(parsed is PackageDetailParse.Parsed)
        val detail = (parsed as PackageDetailParse.Parsed).detail
        assertEquals("com.example.app", detail.packageName)
        assertEquals("0.3.0", detail.versionName)
        assertEquals("39", detail.versionCode)
        assertEquals("10277", detail.uid)
        assertEquals("arm64-v8a", detail.primaryCpuAbi)
        assertEquals("35", detail.minSdk)
        assertEquals("37", detail.targetSdk)
        assertEquals(false, detail.isSystem)
        assertEquals("2026-11-10 06:21:57", detail.firstInstallTime)
        assertEquals("2026-11-10 06:21:57", detail.lastUpdateTime)
        assertEquals("com.android.vending", detail.installerPackageName)
        assertEquals("com.android.vending", detail.initiatingPackageName)
        assertEquals("/data/app/~~abc/com.example.app-base", detail.codePath)
        assertEquals("/data/user/0/com.example.app", detail.dataDir)
        assertEquals("2", detail.apkSigningVersion)
        // requested permissions 两条；runtime permissions 只数用户 0 那两条
        assertEquals(2, detail.requestedPermissionCount)
        assertEquals(2, detail.runtimePermissionCount)
    }

    @Test
    fun `a system flag marks the app as a system app`() {
        val parsed = PackageDetailParser.parse(
            SAMPLE.replace("flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]", "flags=[ SYSTEM HAS_CODE ]"),
            "com.example.app",
        )

        assertEquals(true, (parsed as PackageDetailParse.Parsed).detail.isSystem)
    }

    @Test
    fun `an updated system app is still a system app`() {
        val parsed = PackageDetailParser.parse(
            SAMPLE.replace("flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]", "flags=[ UPDATED_SYSTEM_APP ]"),
            "com.example.app",
        )

        assertEquals(true, (parsed as PackageDetailParse.Parsed).detail.isSystem)
    }

    @Test
    fun `crlf output parses the same as lf`() {
        val detail = (PackageDetailParser.parse(SAMPLE.replace("\n", "\r\n"), "com.example.app")
            as PackageDetailParse.Parsed).detail
        assertEquals("com.android.vending", detail.installerPackageName)
        assertEquals(2, detail.runtimePermissionCount)
        assertEquals("arm64-v8a", detail.primaryCpuAbi)
    }

    @Test
    fun `pkgFlags is read when the rom reports package flags there`() {
        val parsed = PackageDetailParser.parse(
            SAMPLE.replace("flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]", "pkgFlags=[ SYSTEM UPDATED_SYSTEM_APP ]"),
            "com.example.app",
        )

        assertEquals(true, (parsed as PackageDetailParse.Parsed).detail.isSystem)
    }

    @Test
    fun `runtime permissions of a secondary user are not counted`() {
        val userTen = "    User 10: ceDataInode=12 installed=true\n" +
            "      firstInstallTime=2026-11-11 09:00:00\n"
        val userTenWithRuntime = userTen +
            "      runtime permissions:\n" +
            "        android.permission.CAMERA: granted=true, flags=[ USER_SET ]\n"

        val detail = (PackageDetailParser.parse(
            SAMPLE.replace(userTen, userTenWithRuntime),
            "com.example.app",
        ) as PackageDetailParse.Parsed).detail
        assertEquals(2, detail.runtimePermissionCount)
    }

    @Test
    fun `firstInstallTime comes from user 0 not from an earlier listed user`() {
        val userZero = "    User 0: ceDataInode=5591 installed=true hidden=false suspended=false\n" +
            "      firstInstallTime=2026-11-10 06:21:57\n" +
            "      runtime permissions:\n" +
            "        android.permission.CAMERA: granted=true, flags=[ USER_SET ]\n" +
            "        android.permission.POST_NOTIFICATIONS: granted=false, " +
            "flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]\n"
        val userTen = "    User 10: ceDataInode=12 installed=true\n" +
            "      firstInstallTime=2026-11-11 09:00:00\n"
        // 把用户 10 挪到用户 0 前面：只按「第一个见到的」取值时这里会拿到用户 10 的时间
        val output = SAMPLE.replace(userZero + userTen, userTen + userZero)

        val detail = (PackageDetailParser.parse(output, "com.example.app") as PackageDetailParse.Parsed).detail
        assertEquals("2026-11-10 06:21:57", detail.firstInstallTime)
        assertEquals(2, detail.runtimePermissionCount)
    }

    @Test
    fun `a null device value is reported as unknown`() {
        val parsed = PackageDetailParser.parse(
            SAMPLE.replace("primaryCpuAbi=arm64-v8a", "primaryCpuAbi=null")
                .replace("installerPackageName=com.android.vending", "installerPackageName=null"),
            "com.example.app",
        )

        val detail = (parsed as PackageDetailParse.Parsed).detail
        assertNull(detail.primaryCpuAbi)
        assertNull(detail.installerPackageName)

        // ADB / 未知来源安装：installer 为 null，发起安装的包名仍是来源证据
        val sideloaded = PackageDetailParser.parse(
            SAMPLE
                .replace("installerPackageName=com.android.vending", "installerPackageName=null")
                .replace("initiatingPackageName=com.android.vending", "initiatingPackageName=com.android.shell"),
            "com.example.app",
        ).let { (it as PackageDetailParse.Parsed).detail }
        assertNull(sideloaded.installerPackageName)
        assertEquals("com.android.shell", sideloaded.initiatingPackageName)
    }

    @Test
    fun `versionCodeMajor is folded into a 64 bit version code`() {
        val parsed = PackageDetailParser.parse(SAMPLE, "com.example.app")

        // 没有 versionCodeMajor 时按单值显示
        assertEquals("39", (parsed as PackageDetailParse.Parsed).detail.versionCode)

        val major = PackageDetailParser.parse(
            SAMPLE.replace(
                "versionName=0.3.0",
                "versionName=0.3.0\n    versionCodeMajor=2",
            ),
            "com.example.app",
        )
        assertEquals(
            (2L shl 32 or 39L).toString(),
            (major as PackageDetailParse.Parsed).detail.versionCode,
        )
    }

    @Test
    fun `top-level sections after the package block are ignored`() {
        // 包段之后的 `Queries:` 小节里还有别的包与 User 段：解析到顶层那一列就该停
        val output = SAMPLE.replace(
            "Queries:\n  User 0:\n",
            "Queries:\n" +
                "  Package [com.other.app] (0000000):\n" +
                "    appId=10000\n" +
                "    versionName=1.0.0\n" +
                "    flags=[ SYSTEM ]\n" +
                "  User 0: ceDataInode=1 installed=true\n" +
                "    firstInstallTime=2020-01-01 00:00:00\n",
        )

        val detail = (PackageDetailParser.parse(output, "com.example.app") as PackageDetailParse.Parsed).detail
        assertEquals("com.example.app", detail.packageName)
        assertEquals("0.3.0", detail.versionName)
        assertEquals("10277", detail.uid)
        assertEquals(false, detail.isSystem)
        assertEquals("2026-11-10 06:21:57", detail.firstInstallTime)
    }

    @Test
    fun `a missing package is reported as not found`() {
        val parsed = PackageDetailParser.parse(
            "Unable to find package: com.example.app\n",
            "com.example.app",
        )
        assertEquals(PackageDetailParse.NotFound, parsed)
    }

    @Test
    fun `output without a package block is unrecognized`() {
        val parsed = PackageDetailParser.parse(
            "Packages:\n  Package [com.other.app] (0000000):\n    appId=10000\n",
            "com.example.app",
        )
        assertEquals(PackageDetailParse.Unrecognized, parsed)
    }

    private companion object {
        val SAMPLE = """
            Packages:
              Package [com.example.app] (6d2ee9b):
                userId=10277
                appId=10277
                codePath=/data/app/~~abc/com.example.app-base
                resourcePath=/data/app/~~abc/com.example.app-base
                versionCode=39 minSdk=35 targetSdk=37
                versionName=0.3.0
                splits=[base]
                primaryCpuAbi=arm64-v8a
                flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
                apkSigningVersion=2
                dataDir=/data/user/0/com.example.app
                lastUpdateTime=2026-11-10 06:21:57
                installerPackageName=com.android.vending
                initiatingPackageName=com.android.vending
                requested permissions:
                  android.permission.INTERNET
                  android.permission.CAMERA
                User 0: ceDataInode=5591 installed=true hidden=false suspended=false
                  firstInstallTime=2026-11-10 06:21:57
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ USER_SET ]
                    android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
                User 10: ceDataInode=12 installed=true
                  firstInstallTime=2026-11-11 09:00:00
            Queries:
              User 0:
        """.trimIndent()
    }
}