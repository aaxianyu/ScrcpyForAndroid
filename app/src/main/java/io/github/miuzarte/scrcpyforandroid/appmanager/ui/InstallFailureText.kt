package io.github.miuzarte.scrcpyforandroid.appmanager.ui

import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

/** 安装输出里的失败码（INSTALL_FAILED_* / INSTALL_PARSE_FAILED_*），只取第一个。 */
private val FAILURE_CODE = Regex("INSTALL_[A-Z0-9_]+")

/**
 * 把设备返回的失败原文换成一句能看懂的话；认不出的码返回 null，
 * 调用方继续附原始文本，那是排查问题的唯一依据。
 */
internal fun installFailureReason(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val code = FAILURE_CODE.find(text)?.value
    val messageRes = when (code) {
        "INSTALL_FAILED_USER_RESTRICTED" -> R.string.app_manager_install_failed_user_restricted
        "INSTALL_FAILED_VERSION_DOWNGRADE" -> R.string.app_manager_install_failed_version_downgrade
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" -> R.string.app_manager_install_failed_signature
        "INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES" ->
            R.string.app_manager_install_failed_split_cert
        "INSTALL_FAILED_OLDER_SDK" -> R.string.app_manager_install_failed_older_sdk
        "INSTALL_FAILED_DEPRECATED_SDK_VERSION" ->
            R.string.app_manager_install_failed_deprecated_sdk
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> R.string.app_manager_install_failed_storage
        "INSTALL_FAILED_ALREADY_EXISTS" -> R.string.app_manager_install_failed_already_exists
        "INSTALL_FAILED_INVALID_APK",
        "INSTALL_PARSE_FAILED_NOT_APK",
        "INSTALL_PARSE_FAILED_BAD_MANIFEST",
        "INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME",
        "INSTALL_PARSE_FAILED_MANIFEST_MALFORMED",
        "INSTALL_PARSE_FAILED_MANIFEST_EMPTY",
        "INSTALL_PARSE_FAILED_NO_CERTIFICATES",
        "INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION",
        -> R.string.app_manager_install_failed_corrupt
        "INSTALL_FAILED_NO_MATCHING_ABIS" -> R.string.app_manager_install_failed_abi
        "INSTALL_FAILED_MISSING_SPLIT" -> R.string.app_manager_install_failed_missing_split
        "INSTALL_FAILED_TEST_ONLY" -> R.string.app_manager_install_failed_test_only
        "INSTALL_FAILED_INVALID_URI" -> R.string.app_manager_install_failed_invalid_uri
        "INSTALL_FAILED_VERIFICATION_FAILURE" -> R.string.app_manager_install_failed_verification
        else -> null
    } ?: textReason(text).takeIf { code == null } ?: return null
    return AppRuntime.stringResource(messageRes)
}

/**
 * 会话安装失败时系统会直接抛异常并打印堆栈（例如解析不了的安装包），
 * 输出里没有失败码，只能按异常文案归类。
 */
private fun textReason(text: String): Int? {
    val lower = text.lowercase()
    return when {
        "failed to parse" in lower || "failed to load asset path" in lower ->
            R.string.app_manager_install_failed_corrupt
        "signatures do not match" in lower || "signature mismatch" in lower ->
            R.string.app_manager_install_failed_signature
        "version downgrade" in lower || "downgrade detected" in lower ->
            R.string.app_manager_install_failed_version_downgrade
        "insufficient storage" in lower || "no space left" in lower ->
            R.string.app_manager_install_failed_storage
        "already exists" in lower -> R.string.app_manager_install_failed_already_exists
        "full install must include a base package" in lower ->
            R.string.app_manager_install_failed_missing_base
        "no matching abis" in lower -> R.string.app_manager_install_failed_abi
        else -> null
    }
}
