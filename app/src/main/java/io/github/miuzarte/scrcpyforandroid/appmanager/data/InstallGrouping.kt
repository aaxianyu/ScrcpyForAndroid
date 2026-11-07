package io.github.miuzarte.scrcpyforandroid.appmanager.data

/**
 * 一组同会话安装的部件：容器里的 base 与它的 split 必须在一次提交里装完。
 */
internal data class InstallGroup(val apkPaths: List<String>)

/**
 * 容器里的分组判据。
 *
 * 条目名只用来判 split，容器名只用来认容器格式：用户会把导出的 `.apks` 改名，
 * 也会从别处下载 base 用包名、split 用 `split_config.*` 的包。
 *
 * 这里只负责分组，包名在 base 落到本地之后由归档清单给出。
 */
internal object InstallGrouping {
    private const val SPLIT_PREFIX = "split_"
    private val CONTAINERS = listOf(".apks", ".apkm", ".xapk", ".zip")
    fun isContainer(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return CONTAINERS.any { lower.endsWith(it) }
    }

    /**
     * 把一层里的 `.apk` 条目切成组，底包排在组首。
     *
     * 1. 没有 split ⇒ 每个 apk 各自一组；
     * 2. 有 split ⇒ 整层一组：底包取 `base.apk`、`标签_base.apk`，否则取唯一的非 split，
     *    再否则保持原顺序——多个底包候选谁带哪份分包只有归档清单知道，交给设备报真实原因。
     *
     * 底包必须排首位：会话里的 write 顺序就是安装器认底包的顺序，首位之外的分片都当 split。
     * split 从不单独成组，只带一个 split 的组会以 "Full install must include a base package" 失败。
     */
    fun group(apkPaths: List<String>): List<InstallGroup> {
        if (apkPaths.isEmpty()) return emptyList()
        val stems = apkPaths.map { it.substringAfterLast('/').removeSuffix(".apk") }
        val isSplit = stems.map { it.startsWith(SPLIT_PREFIX, ignoreCase = true) }
        if (isSplit.none { it }) return apkPaths.map { InstallGroup(listOf(it)) }
        val baseIndex = stems.indexOfFirst {
            it.equals("base", ignoreCase = true) || it.endsWith("_base", ignoreCase = true)
        }.takeIf { it >= 0 }
            ?: stems.indices.singleOrNull { !isSplit[it] }
        val ordered = if (baseIndex == null) {
            apkPaths
        } else {
            listOf(apkPaths[baseIndex]) + apkPaths.filterIndexed { index, _ -> index != baseIndex }
        }
        return listOf(InstallGroup(ordered))
    }
}
