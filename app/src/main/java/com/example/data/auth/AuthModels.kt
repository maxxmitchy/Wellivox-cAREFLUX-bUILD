package com.example.data.auth

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val providerIds: List<String> = emptyList()
)

interface AuthListenerToken {
    fun remove()
}
