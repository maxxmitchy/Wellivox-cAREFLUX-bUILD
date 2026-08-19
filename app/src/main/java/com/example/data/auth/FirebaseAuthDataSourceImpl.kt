package com.example.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthDataSourceImpl(
    customAuth: FirebaseAuth? = null
) : AuthDataSource {

    private val auth: FirebaseAuth? by lazy {
        customAuth ?: try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        uid = this.uid,
        email = this.email,
        displayName = this.displayName,
        isEmailVerified = this.isEmailVerified,
        providerIds = this.providerData.mapNotNull { it.providerId }
    )

    override fun getCurrentUser(): AuthUser? {
        return try {
            auth?.currentUser?.toAuthUser()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun signInWithEmailAndPassword(email: String, pass: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User is null after sign in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createUserWithEmailAndPassword(email: String, pass: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User is null after sign up"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user?.toAuthUser()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User is null after Google sign in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            firebaseAuth.currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val profileUpdates = userProfileChangeRequest {
                this.displayName = displayName
            }
            firebaseAuth.currentUser?.updateProfile(profileUpdates)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reloadUser(): Result<AuthUser> {
        val firebaseAuth = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            firebaseAuth.currentUser?.reload()?.await()
            val user = firebaseAuth.currentUser?.toAuthUser()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User is null after reload"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun addAuthStateListener(onUserChanged: (AuthUser?) -> Unit): AuthListenerToken {
        val firebaseAuth = auth ?: return object : AuthListenerToken {
            override fun remove() {}
        }
        val listener = FirebaseAuth.AuthStateListener { fa ->
            onUserChanged(fa.currentUser?.toAuthUser())
        }
        try {
            firebaseAuth.addAuthStateListener(listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return object : AuthListenerToken {
            override fun remove() {
                try {
                    firebaseAuth.removeAuthStateListener(listener)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
