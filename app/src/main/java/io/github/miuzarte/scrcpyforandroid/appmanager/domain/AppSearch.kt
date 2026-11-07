package io.github.miuzarte.scrcpyforandroid.appmanager.domain

/**
 * 主列表 / 本机选择器共用的搜索过滤。
 *
 * 匹配前对搜索词做首尾空白修剪（IME 残留的尾随空格不应导致无命中），
 * 应用名与包名子串匹配，忽略大小写。
 */
object AppSearch {

    fun normalizeQuery(rawQuery: String): String = rawQuery.trim()

    fun matches(app: DeviceApp, rawQuery: String): Boolean {
        val query = normalizeQuery(rawQuery)
        if (query.isEmpty()) return true
        return app.displayLabel.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true)
    }

    fun filter(apps: List<DeviceApp>, rawQuery: String): List<DeviceApp> {
        val query = normalizeQuery(rawQuery)
        if (query.isEmpty()) return apps
        return apps.filter { matches(it, query) }
    }
}
