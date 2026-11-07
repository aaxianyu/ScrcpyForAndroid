package io.github.miuzarte.scrcpyforandroid.password

enum class PasswordCreatedState {
    AuthenticatedCreated,
    UnauthenticatedCreated,
    AuthenticatedCreatedModified,
}

val PasswordCreatedState.hasAuthenticatedOrigin: Boolean
    get() = this != PasswordCreatedState.UnauthenticatedCreated

data class PasswordEntry(
    val id: String,
    val name: String,
    val cipherText: CharArray?,
    val createdWithAuth: PasswordCreatedState,
    /** 填充该密码后是否自动补一个回车键 */
    val autoEnter: Boolean = false,
)
