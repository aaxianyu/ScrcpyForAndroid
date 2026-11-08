package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallGroupingTest {
    @Test
    fun baseApkTakesItsSplits() {
        val paths = listOf("/tmp/base.apk", "/tmp/split_config.arm64_v8a.apk")

        assertEquals(listOf(InstallGroup(paths)), InstallGrouping.group(paths))
    }

    @Test
    fun soleNonSplitApkIsTheBase() {
        val paths = listOf("/tmp/app.apk", "/tmp/split_config.zh.apk", "/tmp/split_config.xxhdpi.apk")

        assertEquals(listOf(InstallGroup(paths)), InstallGrouping.group(paths))
    }

    @Test
    fun severalPlainApksEachGetTheirOwnGroup() {
        val paths = listOf("/tmp/a.apk", "/tmp/b.apk")

        assertEquals(paths.map { InstallGroup(listOf(it)) }, InstallGrouping.group(paths))
    }

    /**
     * 一层里既有普通 apk 又有 split 时，二者属于哪个应用只能靠归档清单判断；
     * 合成一组会把不相干的应用塞进同一次会话，所以整层交出去，由设备报出原因。
     */
    @Test
    fun plainApksMixedWithSplitsStayInOneBatch() {
        val paths = listOf("/tmp/a.apk", "/tmp/b.apk", "/tmp/split_config.zh.apk")

        assertEquals(listOf(InstallGroup(paths)), InstallGrouping.group(paths))
    }

    @Test
    fun splitsWithoutABaseStayInOneBatch() {
        val paths = listOf("/tmp/split_config.zh.apk", "/tmp/split_config.xxhdpi.apk")

        assertEquals(listOf(InstallGroup(paths)), InstallGrouping.group(paths))
    }

    @Test
    fun labelPrefixedBaseStillGroupsAsOne() {
        val paths = listOf("/tmp/2048_base.apk", "/tmp/split_config.arm64_v8a.apk")

        assertEquals(listOf(InstallGroup(paths)), InstallGrouping.group(paths))
    }

    @Test
    fun emptyInputIsEmpty() {
        assertEquals(emptyList<InstallGroup>(), InstallGrouping.group(emptyList()))
    }

    @Test
    fun containerSuffixesAreRecognized() {
        assertEquals(true, InstallGrouping.isContainer("app.apks"))
        assertEquals(true, InstallGrouping.isContainer("APP.ZIP"))
        assertEquals(true, InstallGrouping.isContainer("bundle.apkm"))
        assertEquals(false, InstallGrouping.isContainer("app.apk"))
    }

    @Test
    fun baseNotFirstIsMovedToFront() {
        val paths = listOf(
            "/tmp/split_config.en.apk",
            "/tmp/split_config.xxhdpi.apk",
            "/tmp/2048_base.apk",
        )

        assertEquals(paths[2], InstallGrouping.group(paths).single().apkPaths.first())
    }
}
