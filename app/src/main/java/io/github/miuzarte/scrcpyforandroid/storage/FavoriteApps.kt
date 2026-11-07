package io.github.miuzarte.scrcpyforandroid.storage

/**
 * 「收藏应用」偏好值的编解码。
 *
 * 存储形态是逗号分隔的包名清单（偏好键 `favorite_apps`），应用面板按它把收藏的应用
 * 置顶。解析对脏值容错：空段跳过、段首尾空白去除、重复去重；写回按包名排序，
 * 同一集合写出的值稳定（偏好值可读、便于比对）。
 */

fun parseFavoriteApps(stored: String): Set<String> =
    stored.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

fun joinFavoriteApps(packages: Collection<String>): String =
    packages.filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .joinToString(",")

/** 切换一个包的收藏状态并返回新的存储值：解析 → 增删 → 写回在一次调用内完成。 */
fun toggleFavorite(stored: String, packageName: String): String {
    val current = parseFavoriteApps(stored).toMutableSet()
    if (packageName in current) current.remove(packageName) else current.add(packageName)
    return joinFavoriteApps(current)
}

/**
 * 按当前设备清单对账并返回新的存储值。
 *
 * 「清单里没有」只是候选：应用清单可能被截断 / 正则漏匹配，只凭缺失就删会把仍装着的
 * 应用收藏永久删掉（重装不复活）。故只有同时出现在 [confirmedMissing]（逐个复核过
 * `pm path`、确认未安装）里的包名才剔除；复核失败的不在集合里，一律保留。
 *
 * 值没变化时原样返回，不给调用方制造无意义的写盘。
 */
fun pruneFavorites(
    stored: String,
    livePackages: Set<String>,
    confirmedMissing: Set<String>,
): String {
    val current = parseFavoriteApps(stored)
    val kept = current.filter { it in livePackages || it !in confirmedMissing }
    if (kept.size == current.size) return stored
    return joinFavoriteApps(kept)
}
