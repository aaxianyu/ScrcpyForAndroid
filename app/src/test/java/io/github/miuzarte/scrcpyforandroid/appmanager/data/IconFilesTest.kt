package io.github.miuzarte.scrcpyforandroid.appmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 图标文件布局的真文件系统验证。
 *
 * 跑在真实临时目录上：这里要验的就是「文件落下去之后名字对不对、旧的有没有被清掉」，
 * 拿内存假目录测等于把要验的东西换掉了。
 *
 * 覆盖的关键场景在真机上验不到：编码从 WebP 回退到 PNG（需要一台 Android 11 以下的
 * 设备）以及随之而来的换编码清理。
 */
class IconFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun dir(): File = folder.newFolder("icons")

    private fun names(dir: File): List<String> =
        dir.listFiles().orEmpty().map { it.name }.sorted()

    // ---------------------------------------------------------------- 后缀映射

    @Test
    fun `suffix follows the encoding the probe reported`() {
        assertEquals(".png", IconFiles.suffixFor("png"))
        assertEquals(".webp", IconFiles.suffixFor("webp"))
        assertEquals(".webp", IconFiles.suffixFor("WEBP"))
    }

    @Test
    fun `unrecognized or absent encoding falls back to webp`() {
        assertEquals(".webp", IconFiles.suffixFor(""))
        assertEquals(".webp", IconFiles.suffixFor("jpeg"))
    }

    @Test
    fun `file names resolve to package names for every known suffix`() {
        assertEquals("com.a", IconFiles.packageOf("com.a.webp"))
        assertEquals("com.a", IconFiles.packageOf("com.a.png"))
        // 包名里本身带点（厂商域名前缀）不影响反解
        assertEquals("com.zui.antitheft", IconFiles.packageOf("com.zui.antitheft.png"))
    }

    @Test
    fun `names that are not icons resolve to null`() {
        assertNull(IconFiles.packageOf("notes.txt"))
        // 只有后缀没有包名：目录里混进别的 .png 时必须认出来它不是图标，
        // 否则清理会把用户的截图当成空包名的孤儿处理
        assertNull(IconFiles.packageOf(".webp"))
        assertNull(IconFiles.packageOf(".png"))
    }

    // ---------------------------------------------------------------- 写入与查找

    @Test
    fun `icons are written under the suffix of their actual encoding`() {
        val dir = dir()
        IconFiles.write(dir, "com.a", byteArrayOf(1, 2, 3), ".png")
        assertEquals(listOf("com.a.png"), names(dir))
        assertTrue(File(dir, "com.a.png").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `find locates an icon regardless of which suffix it was written under`() {
        val dir = dir()
        IconFiles.write(dir, "com.png.pkg", byteArrayOf(1), ".png")
        assertEquals("com.png.pkg.png", IconFiles.find(dir, "com.png.pkg")?.name)

        IconFiles.write(dir, "com.webp.pkg", byteArrayOf(1), ".webp")
        assertEquals("com.webp.pkg.webp", IconFiles.find(dir, "com.webp.pkg")?.name)
    }

    @Test
    fun `find returns null when the package has no icon file`() {
        assertNull(IconFiles.find(dir(), "com.a"))
    }

    // ---------------------------------------------------------------- 换编码

    @Test
    fun `switching a package from webp to png drops the stale webp file`() {
        val dir = dir()
        IconFiles.write(dir, "com.a", byteArrayOf(9), ".webp")
        assertEquals(listOf("com.a.webp"), names(dir))

        // 设备换编码后重写：旧后缀的文件必须消失，否则按固定顺序查找会先撞上过期字节，
        // 表现为图标不随列表刷新
        IconFiles.write(dir, "com.a", byteArrayOf(7, 7), ".png")
        assertEquals(listOf("com.a.png"), names(dir))
        assertTrue(File(dir, "com.a.png").readBytes().contentEquals(byteArrayOf(7, 7)))
    }

    @Test
    fun `switching back from png to webp also drops the stale file`() {
        val dir = dir()
        IconFiles.write(dir, "com.a", byteArrayOf(1), ".png")
        IconFiles.write(dir, "com.a", byteArrayOf(2), ".webp")
        assertEquals(listOf("com.a.webp"), names(dir))
    }

    @Test
    fun `rewriting a package leaves other packages on their own suffix`() {
        val dir = dir()
        IconFiles.write(dir, "com.old", byteArrayOf(1), ".webp")
        IconFiles.write(dir, "com.new", byteArrayOf(2), ".png")
        assertEquals(listOf("com.new.png", "com.old.webp"), names(dir))
        assertEquals("com.old.webp", IconFiles.find(dir, "com.old")?.name)
    }

    // ---------------------------------------------------------------- 删除

    @Test
    fun `delete removes every known suffix of the package`() {
        val dir = dir()
        IconFiles.write(dir, "com.a", byteArrayOf(1), ".webp")
        File(dir, "com.a.png").writeBytes(byteArrayOf(2))
        File(dir, "com.b.webp").writeBytes(byteArrayOf(3))

        IconFiles.delete(dir, "com.a")
        assertEquals(listOf("com.b.webp"), names(dir))
    }

    @Test
    fun `deleting a package with no icon file is a no-op`() {
        val dir = dir()
        IconFiles.write(dir, "com.b", byteArrayOf(1), ".webp")
        IconFiles.delete(dir, "com.a")
        assertEquals(listOf("com.b.webp"), names(dir))
    }

    // ---------------------------------------------------------------- 孤儿清理

    @Test
    fun `prune drops icons of packages no longer on the device in either suffix`() {
        val dir = dir()
        // 两个孤儿，分别落在两种后缀上——只认一种后缀就会漏掉另一个
        File(dir, "com.gone.webp").writeBytes(byteArrayOf(1))
        File(dir, "com.gone2.png").writeBytes(byteArrayOf(2))
        File(dir, "com.live.webp").writeBytes(byteArrayOf(3))
        File(dir, "com.live.png").writeBytes(byteArrayOf(4))

        IconFiles.pruneOrphans(dir, setOf("com.live"))

        assertEquals(listOf("com.live.png", "com.live.webp"), names(dir))
    }

    @Test
    fun `prune leaves files that are not icons alone`() {
        val dir = dir()
        File(dir, "com.gone.webp").writeBytes(byteArrayOf(1))
        File(dir, ".png").writeBytes(byteArrayOf(2))
        File(dir, "readme.txt").writeText("x")

        IconFiles.pruneOrphans(dir, emptySet())

        // 孤儿删掉，两个非图标文件留着
        assertEquals(listOf(".png", "readme.txt"), names(dir))
        assertTrue(File(dir, ".png").exists())
        assertTrue(File(dir, "readme.txt").exists())
    }

    @Test
    fun `prune on a directory with no files does nothing`() {
        val dir = dir()
        IconFiles.pruneOrphans(dir, setOf("com.a"))
        assertTrue(names(dir).isEmpty())
    }

    @Test
    fun `prune keeps every suffix of a package that is still installed`() {
        val dir = dir()
        File(dir, "com.live.webp").writeBytes(byteArrayOf(1))
        File(dir, "com.live.png").writeBytes(byteArrayOf(2))

        IconFiles.pruneOrphans(dir, setOf("com.live"))

        assertEquals(listOf("com.live.png", "com.live.webp"), names(dir))
    }
}
