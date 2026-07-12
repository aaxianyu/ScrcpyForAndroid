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
    const val RELEASES_API_URL =
        "https://api.github.com/repos/aaxianyu/ScrcpyForAndroid/releases?per_page=10"
    const val RELEASES_URL =
        "https://github.com/aaxianyu/ScrcpyForAndroid/releases"
    const val REPO_URL =
        "https://github.com/Miuzarte/ScrcpyForAndroid"
    const val UPSTREAM_REPO_URL =
        "https://github.com/Miuzarte/ScrcpyForAndroid"
    const val UPSTREAM_RELEASES_API_URL =
        "https://api.github.com/repos/Miuzarte/ScrcpyForAndroid/releases?per_page=10"
    const val UPSTREAM_RELEASES_URL =
        "https://github.com/Miuzarte/ScrcpyForAndroid/releases"
    const val BRANCH_REPO_URL =
        "https://github.com/aaxianyu/ScrcpyForAndroid"
    const val BRANCH_RELEASES_URL =
        "https://github.com/aaxianyu/ScrcpyForAndroid/releases"
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

    suspend fun ensureChecked(currentVersion: String = BuildConfig.VERSION_NAME) {
        checkMutex.withLock {
            if (_state.value is State.Ready || _state.value is State.Checking) return
            _state.value = State.Checking
            runCatching { fetchLatestRelease(currentVersion) }
                .onSuccess { _state.value = State.Ready(it) }
                .onFailure { error ->
                    EventLogger.logEvent(
                        R.string.main_update_check_failed,
                        error.message ?: error.javaClass.simpleName,
                        level = Log.WARN,
                        error = error,
                    )
                    _state.value = State.Error
                }
        }
    }

    suspend fun ensureUpstreamChecked(currentVersion: String = BuildConfig.VERSION_NAME) {
        upstreamMutex.withLock {
            if (_upstreamState.value is State.Ready || _upstreamState.value is State.Checking) return
            _upstreamState.value = State.Checking
            runCatching { fetchLatestRelease(currentVersion, UPSTREAM_RELEASES_API_URL, UPSTREAM_RELEASES_URL) }
                .onSuccess { _upstreamState.value = State.Ready(it) }
                .onFailure { error ->
                    EventLogger.logEvent(
                        R.string.main_update_check_failed,
                        error.message ?: error.javaClass.simpleName,
                        level = Log.WARN,
                        error = error,
                    )
                    _upstreamState.value = State.Error
                }
        }
    }

    private suspend fun fetchLatestRelease(
        currentVersion: String,
        apiUrl: String = RELEASES_API_URL,
        fallbackUrl: String = RELEASES_URL,
    ): ReleaseInfo =
        withContext(Dispatchers.IO) {
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ScrcpyForAndroid/$currentVersion")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    error("HTTP $responseCode from GitHub releases API")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(body)
                val currentIsPre = currentVersion.contains("_pre")

                val targetRelease = (0 until releases.length()).asSequence()
                    .mapNotNull { releases.optJSONObject(it) }
                    .firstOrNull { release ->
                        currentIsPre || !release.optBoolean("prerelease", false)
                    } ?: error("No suitable release found")

                val latestVersion = targetRelease.optString("tag_name")
                    .ifBlank { targetRelease.optString("name") }
                    .ifBlank { error("GitHub release has no tag name") }
                ReleaseInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion.removePrefix("v").removePrefix("V"),
                    hasUpdate = compareVersions(currentVersion, latestVersion) < 0,
                    htmlUrl = targetRelease.optString("html_url").ifBlank { fallbackUrl },
                )
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
