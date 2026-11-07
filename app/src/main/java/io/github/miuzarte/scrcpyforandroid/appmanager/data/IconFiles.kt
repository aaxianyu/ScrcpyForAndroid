package io.github.miuzarte.scrcpyforandroid.appmanager.data

import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 图标文件在目录里的布局规则。
 *
 * 单独抽出来是为了能纯 JVM 测。[DeviceAppCache] 本体碰 Context、`LruCache` 与
 * `BitmapFactory`，单测里都跑不起来，而这层「文件名 ↔ 包名 ↔ 后缀」的文件操作恰恰是
 * 出错后最难在设备上察觉的部分：文件名与字节对不上不会崩，只会让图标整列空白；
 * 而要在真机上看到编码回退，得一台不支持 `WEBP_LOSSY` 的机器。
 *
 * 所以这里不引用任何 Android 类型，[DeviceAppCache] 把目录交给本对象，其余自己管。
 *
 * 写、删、清理都串在一条锁上：临时文件 + 重命名 + 跨后缀删除是组合操作，应用管理页的
 * 后台补齐与面板的按需拉取并行时，会互相删掉对方正在写的文件。
 */
internal object IconFiles {

    /**
     * 全部已知的图标文件后缀，顺序即查找优先级。
     *
     * 探测端按设备能力选编码（`WEBP_LOSSY` 是 API 30 才有，老设备回退 PNG），所以落盘
     * 后缀不是常量。读、删、清理三处都按这份表来，少一处就会漏掉换编码前写下的旧文件。
     */
    val SUFFIXES = listOf(".webp", ".png")

    /** 探测端报回的编码名 → 落盘后缀；认不出的一律按 WebP 兜底（探测端默认就是它）。 */
    fun suffixFor(format: String): String =
        if (format.lowercase() == "png") PNG else SUFFIXES.first()

    /**
     * 图标文件名反解包名：不是图标文件时返回 null。
     *
     * 只有后缀没有包名不算图标文件——目录里若混进了别的 `.png`，少了这条判据就会被
     * 当成「包名是空串的孤儿」删掉或保留。
     */
    fun packageOf(name: String): String? {
        for (suffix in SUFFIXES) {
            if (name.length > suffix.length && name.endsWith(suffix)) {
                return name.removeSuffix(suffix)
            }
        }
        return null
    }

    /**
     * 写入图标，并保证同一包只留一份；返回是否写入成功。
     *
     * 设备换编码（ROM 升级换了压缩能力）后旧后缀的文件会留在目录里，而 [find] 按固定
     * 顺序找文件，先撞上的可能是上一份过期字节——用户看到的是图标不更新，不是报错。
     *
     * 先写临时文件再改名：写盘中断/磁盘写满只会留下临时文件（被 [find]、[pruneOrphans]
     * 忽略，由 [pruneStaleTemp] 收敛），不会留下半截的正式文件被当成「已有图标」永远不再重拉。
     * 临时名带纳秒后缀，两次写不会撞名。
     */
    fun write(dir: File, packageName: String, bytes: ByteArray, suffix: String): Boolean = fileLock.withLock {
        dir.mkdirs()
        val target = File(dir, packageName + suffix)
        val temp = File(dir, "$packageName$suffix$TEMP_MARK${System.nanoTime()}")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                // 少数文件系统不允许直接覆盖已存在的目标：删掉再来一次。
                // 源文件已不在（被并发写移走）时不删目标——宁可留旧的，也不留空
                if (!temp.exists()) return@withLock false
                target.delete()
                if (!temp.renameTo(target)) return@withLock false
            }
        } finally {
            temp.delete()
        }
        for (other in SUFFIXES) {
            if (other != suffix) {
                File(dir, packageName + other).delete()
            }
        }
        true
    }

    /**
     * 定位某包的图标文件，按 [SUFFIXES] 顺序找；一个都没有时返回 null。
     *
     * 0 字节文件视为缺失（历史版本非原子写中断留下的残骸）：还它 true 会让「已有图标」
     * 永远成立，界面一直空白且从不重拉。
     */
    fun find(dir: File, packageName: String): File? {
        for (suffix in SUFFIXES) {
            val file = File(dir, packageName + suffix)
            if (file.exists() && file.length() > 0L) return file
        }
        return null
    }

    /** 删除该包全部已知后缀的文件：卸载只知道自己要删哪个包，不知道当前是哪种编码。 */
    fun delete(dir: File, packageName: String) = fileLock.withLock {
        for (suffix in SUFFIXES) {
            File(dir, packageName + suffix).delete()
        }
    }

    /**
     * 清掉目录里已不在设备上的孤儿文件。
     *
     * 设备上自行卸载、系统更新移除这些外部变化，客户端拿不到事件，只能靠每次全量写入
     * 拿真值收敛——不扫的话图标目录只增不减。匹配认全部已知后缀：写盘用的后缀由设备
     * 能力决定，会随设备变化，只认一种就会漏掉另一种写下的孤儿。
     */
    fun pruneOrphans(dir: File, livePackages: Set<String>) = fileLock.withLock {
        dir.listFiles()?.forEach { file ->
            val packageName = packageOf(file.name) ?: return@forEach
            if (packageName !in livePackages) {
                file.delete()
            }
        }
    }

    /**
     * 清掉中断写入留下的临时文件（进程被杀 / 掉电时 finally 不会执行）。
     *
     * 临时名带唯一后缀，正常路径每次写都会清自己的那份；这里只按时间清理陈旧的，
     * 不碰正在进行中的写。
     */
    fun pruneStaleTemp(dir: File, olderThanMs: Long = STALE_TEMP_MS) = fileLock.withLock {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { file ->
            if (file.name.contains(TEMP_MARK) && now - file.lastModified() > olderThanMs) {
                file.delete()
            }
        }
    }

    /** 读写闸：见文件头说明。 */
    private val fileLock = ReentrantLock()

    private const val PNG = ".png"

    /** 临时文件标记：不属于任何已知图标后缀，[packageOf] / [find] / [pruneOrphans] 都不会碰它。 */
    private const val TEMP_MARK = ".tmp-"

    /** 陈旧临时文件的年龄阈值：超过它一定不再是「进行中的写」。 */
    private const val STALE_TEMP_MS = 10 * 60_000L
}
