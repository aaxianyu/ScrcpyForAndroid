package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按包名取应用名与图标时，包名要拼进 `app_process … AppProbe` 后面，整条命令得过 `adb shell`。
 * 命令行超长不会报错，只会把命令截掉——探测端拿不到完整参数就只回一部分应用，看起来跟
 * 「这些包都没取到名字和图标」一模一样。分批就是防这个静默截断。
 */
class ProbeFilterBatchingTest {

    @Test
    fun aTypicalDeviceStaysInASingleBatch() {
        val packages = (1..120).map { "com.vendor.app$it" }
        assertEquals(listOf(packages), splitIntoBatches(packages))
    }

    @Test
    fun noPackageNamesMeansNoBatches() {
        assertTrue(splitIntoBatches(emptyList()).isEmpty())
    }

    @Test
    fun everyBatchStaysUnderTheByteBudget() {
        // 远超任何真实设备的包名数量：真机上跑着的应用没有上千个
        val packages = (1..5000).map { "com.vendor.app$it" }
        val batches = splitIntoBatches(packages)
        assertTrue(batches.size > 1)
        batches.forEach { batch ->
            val commandBytes = batch.sumOf { it.length + 1 }
            assertTrue(
                "batch of ${batch.size} names is $commandBytes bytes",
                commandBytes <= PROBE_FILTER_BATCH_BYTES,
            )
        }
    }

    @Test
    fun splittingNeitherDropsNorRepeatsAPackageName() {
        val packages = (1..3000).map { "com.vendor.app$it" }
        val flattened = splitIntoBatches(packages).flatten()
        assertEquals(packages, flattened)
        assertEquals(packages.size, flattened.toSet().size)
    }

    @Test
    fun aSingleOversizedNameStillGetsABatchOfItsOwn() {
        // 单个包名就超预算时不能把它丢掉，也不能让它无限膨胀
        val huge = "com." + "x".repeat(PROBE_FILTER_BATCH_BYTES)
        val batches = splitIntoBatches(listOf("com.a", huge, "com.b"))
        assertEquals(listOf("com.a"), batches[0])
        assertEquals(listOf(huge), batches[1])
        assertEquals(listOf("com.b"), batches[2])
    }
}
