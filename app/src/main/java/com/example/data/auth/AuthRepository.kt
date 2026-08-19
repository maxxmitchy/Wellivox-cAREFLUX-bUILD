package com.example.data.auth

class AuthRepository(
    private val authDataSource: AuthDataSource = FirebaseAuthDataSourceImpl()
) {
    fun getCurrentUser(): AuthUser? = authDataSource.getCurrentUser()
    suspend fun signInWithEmailAndPassword(email: String, pass: String): Result<AuthUser> =
        authDataSource.signInWithEmailAndPassword(email, pass)
    suspend fun createUserWithEmailAndPassword(email: String, pass: String): Result<AuthUser> =
        authDataSource.createUserWithEmailAndPassword(email, pass)
    suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> =
        authDataSource.signInWithGoogleIdToken(idToken)
    suspend fun sendEmailVerification(): Result<Unit> =
        authDataSource.sendEmailVerification()
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        authDataSource.sendPasswordResetEmail(email)
    suspend fun updateDisplayName(displayName: String): Result<Unit> =
        authDataSource.updateDisplayName(displayName)
    suspend fun reloadUser(): Result<AuthUser> =
        authDataSource.reloadUser()
    fun signOut() = authDataSource.signOut()
    fun addAuthStateListener(onUserChanged: (AuthUser?) -> Unit): AuthListenerToken =
        authDataSource.addAuthStateListener(onUserChanged)
}
