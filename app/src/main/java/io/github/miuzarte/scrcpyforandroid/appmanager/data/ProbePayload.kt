package io.github.miuzarte.scrcpyforandroid.appmanager.data

import kotlinx.serialization.Serializable

/**
 * 探测程序输出的应用条目。
 *
 * 字段名与 `app/probe/java/AppProbe.java` 的输出严格一致；
 * [iconBase64] 为图标原始字节的 Base64（不二次重编码），缺失表示取不到图标。
 *
 * 字节是什么编码由同级的 [ProbePayload.iconFmt] 说明，不靠字段名暗示——探测端在不支持
 * `WEBP_LOSSY` 的设备上会回退成 PNG。
 */
@Serializable
data class ProbeApp(
    val pkg: String,
    val label: String = "",
    val ver: String = "",
    val size: Long = 0L,
    /** 分包数量：> 0 表示该应用有 split 分包，导出产物是 zip */
    val split: Int = 0,
    val sys: Boolean = false,
    val enabled: Boolean = true,
    val iconBase64: String? = null,
)

@Serializable
data class ProbePayload(
    val apps: List<ProbeApp> = emptyList(),
    /** 图标实际编码：`webp` / `png`；空串 = 探测端没报，按 WebP 兜底 */
    val iconFmt: String = "",
    /** 探测端回传的首个图标失败原因；老版本探测端没有该字段，默认空串 */
    val iconErr: String = "",
)
