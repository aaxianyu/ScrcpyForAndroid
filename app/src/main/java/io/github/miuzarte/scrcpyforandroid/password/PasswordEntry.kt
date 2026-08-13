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
    val autoEnter: Boolean = false,
)
