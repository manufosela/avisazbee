package com.manufosela.avisazbee.infrastructure.firebase.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.manufosela.avisazbee.BuildConfig
import com.manufosela.avisazbee.features.auth.domain.AppUser
import com.manufosela.avisazbee.features.auth.domain.AuthFailure
import com.manufosela.avisazbee.features.auth.domain.AuthRepository
import com.manufosela.avisazbee.infrastructure.lifecycle.ActivityHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val activityHolder: ActivityHolder,
    @ApplicationContext private val appContext: Context,
) : AuthRepository {

    override fun authStateChanges(): Flow<AppUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAppUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override val currentUser: AppUser?
        get() = auth.currentUser?.toAppUser()

    override suspend fun signInWithGoogle(): AppUser {
        val activity = activityHolder.currentActivity
            ?: throw AuthFailure("unknown", "no foreground activity available")
        val credentialManager = CredentialManager.create(appContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            credentialManager.getCredential(activity, request)
        } catch (cancellation: GetCredentialCancellationException) {
            throw AuthFailure("cancelled", cancellation.message)
        } catch (noCred: NoCredentialException) {
            throw AuthFailure("cancelled", noCred.message)
        } catch (network: IOException) {
            throw AuthFailure("network", network.message)
        } catch (generic: GetCredentialException) {
            throw AuthFailure("unknown", generic.message)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw AuthFailure("unknown", "unexpected credential type: ${credential.type}")
        }

        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (parsing: GoogleIdTokenParsingException) {
            throw AuthFailure("unknown", parsing.message)
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val result = try {
            auth.signInWithCredential(firebaseCredential).await()
        } catch (network: IOException) {
            throw AuthFailure("network", network.message)
        } catch (error: Exception) {
            throw AuthFailure("unknown", error.message)
        }
        return result.user?.toAppUser()
            ?: throw AuthFailure("unknown", "Firebase returned no user after sign-in")
    }

    override suspend fun signOut() {
        auth.signOut()
    }
}

private fun FirebaseUser.toAppUser(): AppUser = AppUser(
    uid = uid,
    email = email,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    createdAt = metadata?.creationTimestamp
        ?.let(Instant::ofEpochMilli)
        ?: Instant.EPOCH,
)
