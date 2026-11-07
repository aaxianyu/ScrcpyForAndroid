package io.github.miuzarte.scrcpyforandroid.services

import android.util.Log
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateChecker {
    private const val TAG = "AppUpdateChecker"

    /** 本分支仓库: 更新检测和「关于」页都指向这里 */
    const val RELEASES_API_URL = "https://api.github.com/repos/aaxianyu/ScrcpyForAndroid/releases?per_page=20"
    const val RELEASES_URL = "https://github.com/aaxianyu/ScrcpyForAndroid/releases"
    const val REPO_URL = "https://github.com/aaxianyu/ScrcpyForAndroid"

    /** 上游仓库: 只在「关于」页只读展示, 不参与提示更新 */
    const val UPSTREAM_RELEASES_API_URL =
        "https://api.github.com/repos/miuzarte/ScrcpyForAndroid/releases?per_page=10"
    const val UPSTREAM_RELEASES_URL = "https://github.com/miuzarte/ScrcpyForAndroid/releases"
    const val UPSTREAM_REPO_URL = "https://github.com/miuzarte/ScrcpyForAndroid"

    /** 本分支 release 的 tag 约定: 上游版本号 + e（如 v0.7.2e）, 用来和上游 release 区分 */
    private val OWN_TAG_REGEX = Regex("^[vV]?[0-9]+(?:\\.[0-9]+)*e[0-9]*$")

    const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    data class ReleaseInfo(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val htmlUrl: String,
    )

    sealed interface State {
        data object Idle: State
        data object Checking: State
        data object Error: State
        data class Ready(val release: ReleaseInfo): State
    }

    private val checkMutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val upstreamMutex = Mutex()
    private val _upstreamState = MutableStateFlow<State>(State.Idle)
    val upstreamState: StateFlow<State> = _upstreamState.asStateFlow()

    // 记录上次真正发起请求的时间: 12 小时内重开页面直接复用内存结果,
    // 不再打 GitHub API（匿名额度只有 60 次/时/IP）
    private var lastOwnRequestAtMs = 0L
    private var lastUpstreamRequestAtMs = 0L

    suspend fun ensureChecked(currentVersion: String = BuildConfig.VERSION_NAME) {
        checkMutex.withLock {
            if (isFresh(lastOwnRequestAtMs, _state.value)) return
            _state.value = State.Checking
            lastOwnRequestAtMs = System.currentTimeMillis()
            runCatching { fetchLatestRelease(currentVersion) }
                .onSuccess { _state.value = State.Ready(it) }
                .onFailure { error ->
                    logFailure(error)
                    _state.value = State.Error
                }
        }
    }

    /** 上游版本只读展示用: 同样 12h 一次, 与 [ensureChecked] 互不影响 */
    suspend fun ensureUpstreamChecked() {
        upstreamMutex.withLock {
            if (isFresh(lastUpstreamRequestAtMs, _upstreamState.value)) return
            _upstreamState.value = State.Checking
            lastUpstreamRequestAtMs = System.currentTimeMillis()
            runCatching { fetchLatestUpstreamRelease() }
                .onSuccess { _upstreamState.value = State.Ready(it) }
                .onFailure { error ->
                    logFailure(error)
                    _upstreamState.value = State.Error
                }
        }
    }

    /** 12h 内且已有结论（成功或失败）就不再重复请求 */
    private fun isFresh(requestedAtMs: Long, state: State): Boolean {
        if (state is State.Checking || state is State.Idle) return false
        return System.currentTimeMillis() - requestedAtMs < CHECK_INTERVAL_MS
    }

    private fun logFailure(error: Throwable) {
        EventLogger.logEvent(
            R.string.main_update_check_failed,
            error.message ?: error.javaClass.simpleName,
            level = Log.WARN,
            error = error,
        )
    }

    private suspend fun fetchLatestRelease(currentVersion: String): ReleaseInfo =
        withContext(Dispatchers.IO) {
            val releases = requestReleases(RELEASES_API_URL)
            val currentIsPre = currentVersion.contains("_pre")

            // 只看本分支自己的 release: tag 必须带 e 后缀, 避免把上游的 v0.7.x 当成"我们的新版本"
            val targetRelease = (0 until releases.length()).asSequence()
                .mapNotNull { releases.optJSONObject(it) }
                .firstOrNull { release ->
                    OWN_TAG_REGEX.matches(release.optString("tag_name").trim())
                            && (currentIsPre || !release.optBoolean("prerelease", false))
                } ?: error("No suitable branch release found")

            val latestVersion = targetRelease.optString("tag_name")
                .ifBlank { targetRelease.optString("name") }
                .ifBlank { error("GitHub release has no tag name") }
            ReleaseInfo(
                currentVersion = currentVersion,
                latestVersion = latestVersion.removePrefix("v").removePrefix("V"),
                hasUpdate = compareVersions(currentVersion, latestVersion) < 0,
                htmlUrl = targetRelease.optString("html_url").ifBlank { RELEASES_URL },
            )
        }

    /** 上游最新正式版: 跳过预发布, 取列表里第一个非预发布项 */
    private suspend fun fetchLatestUpstreamRelease(): ReleaseInfo =
        withContext(Dispatchers.IO) {
            val releases = requestReleases(UPSTREAM_RELEASES_API_URL)
            val targetRelease = (0 until releases.length()).asSequence()
                .mapNotNull { releases.optJSONObject(it) }
                .firstOrNull { release -> !release.optBoolean("prerelease", false) }
                ?: error("No upstream release found")
            val latestVersion = targetRelease.optString("tag_name")
                .ifBlank { targetRelease.optString("name") }
                .ifBlank { error("GitHub release has no tag name") }
            ReleaseInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = latestVersion.removePrefix("v").removePrefix("V"),
                hasUpdate = false,
                htmlUrl = targetRelease.optString("html_url").ifBlank { UPSTREAM_RELEASES_URL },
            )
        }

    private suspend fun requestReleases(apiUrl: String): JSONArray =
        withContext(Dispatchers.IO) {
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ScrcpyForAndroid/${BuildConfig.VERSION_NAME}")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    error("HTTP $responseCode from GitHub releases API")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JSONArray(body)
            } finally {
                connection.disconnect()
            }
        }

    private data class Version(
        val parts: List<Int>,
        val isPre: Boolean,
        val preNum: Int,
    )

    private fun compareVersions(current: String, latest: String): Int {
        val cur = parseVersion(current)
        val lte = parseVersion(latest)
        val maxSize = maxOf(cur.parts.size, lte.parts.size)
        for (i in 0 until maxSize) {
            val cp = cur.parts.getOrElse(i) { 0 }
            val lp = lte.parts.getOrElse(i) { 0 }
            if (cp != lp) return cp.compareTo(lp)
        }
        return when {
            !cur.isPre && !lte.isPre -> 0
            !cur.isPre -> 1
            !lte.isPre -> -1
            else -> cur.preNum.compareTo(lte.preNum)
        }
    }

    private fun parseVersion(value: String): Version {
        val cleaned = value.trim().removePrefix("v").removePrefix("V")
        val preMatch = Regex("_(?:pre|rc|beta|alpha)(\\d*)$", RegexOption.IGNORE_CASE).find(cleaned)
        val mainPart = preMatch?.range?.let { cleaned.substring(0, it.first) } ?: cleaned
        val parts = mainPart.split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() }
            .ifEmpty { listOf(0) }
        val isPre = preMatch != null
        val preNum = preMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return Version(parts, isPre, preNum)
    }
}
