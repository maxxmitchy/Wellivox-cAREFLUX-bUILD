package com.example.data.auth

interface AuthDataSource {
    fun getCurrentUser(): AuthUser?
    suspend fun signInWithEmailAndPassword(email: String, pass: String): Result<AuthUser>
    suspend fun createUserWithEmailAndPassword(email: String, pass: String): Result<AuthUser>
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun reloadUser(): Result<AuthUser>
    fun signOut()
    fun addAuthStateListener(onUserChanged: (AuthUser?) -> Unit): AuthListenerToken
}
