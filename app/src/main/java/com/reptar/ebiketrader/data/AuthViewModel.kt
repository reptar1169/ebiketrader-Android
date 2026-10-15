package com.reptar.ebiketrader.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.firestore.SetOptions
import com.reptar.ebiketrader.support.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val user: FirebaseUser? = null,
    /** True until Firebase reports the restored session (or the lack of one),
     * so the UI doesn't flash a signed-out state on launch. */
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSignedIn: Boolean get() = user != null
    val uid: String? get() = user?.uid
    val displayName: String get() = user?.displayName ?: user?.email ?: "You"
}

/** Firebase Auth session, shared app-wide. Ports AuthContext.tsx and the iOS
 * AuthStore: the same providers, the same users/{uid} profile bootstrap after
 * sign-in, and the same presence heartbeat that lets the message-notification
 * Cloud Function skip emailing someone who is already looking at the app. */
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _state.update { it.copy(user = user, isLoading = false) }
        if (user != null) {
            viewModelScope.launch { ensureUserProfile(user) }
        }
    }

    init {
        auth.addAuthStateListener(listener)
    }

    override fun onCleared() = auth.removeAuthStateListener(listener)

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    // MARK: Email and password

    fun signIn(email: String, password: String) = perform {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signUp(email: String, password: String, displayName: String) = perform {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val name = displayName.trim().ifEmpty { "Seller" }
        result.user?.updateProfile(userProfileChangeRequest { this.displayName = name })?.await()
        // The auth listener fires before the display name is committed, so the
        // profile is written again here with the real name.
        result.user?.let { ensureUserProfile(it, nameOverride = name) }
    }

    fun sendPasswordReset(email: String) = perform {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    // MARK: Google

    /** Uses GetSignInWithGoogleOption — the explicit-button flow — rather than
     * GetGoogleIdOption. The latter filters to accounts already used with this
     * app, which means a brand-new user gets NoCredentialException and you have
     * to retry with the filter off. Since this is behind a "Continue with
     * Google" button, the full account picker is what people expect anyway. */
    fun signInWithGoogle(context: Context, serverClientId: String) = perform {
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google returned an unexpected credential type.")
        }

        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    fun signOut() {
        auth.signOut()
    }

    /** Calls the same deleteAccount callable the website and iOS app use — it
     * recursively deletes the user's listings, favourites, blocks and Storage
     * prefix, anonymises their name in existing conversations, and removes the
     * Auth user last.
     *
     * Required by Google Play's user-data policy for the same reason Apple
     * requires it: an account created in the app has to be deletable in the
     * app, not only by emailing support. */
    fun deleteAccount(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, errorMessage = null) }
            try {
                FirebaseFunctions.getInstance()
                    .getHttpsCallable("deleteAccount")
                    .call()
                    .await()
                // The Auth user is already gone server-side; this clears the
                // local session and drops every listener bound to the old uid.
                runCatching { auth.signOut() }
                _state.update { it.copy(isWorking = false) }
                onDeleted()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isWorking = false, errorMessage = friendlyMessage(e))
                }
            }
        }
    }

    // MARK: Plumbing

    private fun perform(work: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, errorMessage = null) }
            try {
                work()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = friendlyMessage(e)) }
            }
            _state.update { it.copy(isWorking = false) }
        }
    }

    /** Firebase's raw messages are serviceable but blunt; these are the few
     * cases a real person hits often enough to be worth rewording. Same
     * wording as the iOS port, which matches on numeric codes — Android's SDK
     * exposes stable string codes instead. */
    private fun friendlyMessage(error: Exception): String = when {
        error !is FirebaseAuthException -> error.message ?: "Something went wrong."
        else -> when (error.errorCode) {
            // Firebase deliberately blurs these together when email
            // enumeration protection is on, so one message covers all three.
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                "That email and password don't match an account."
            "ERROR_INVALID_EMAIL" -> "That doesn't look like a valid email address."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "There's already an account with that email. Try signing in instead."
            "ERROR_WEAK_PASSWORD" -> "Pick a password at least 6 characters long."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "You already have an account with that email, created with a different " +
                    "sign-in method. Use the one you signed up with."
            else -> error.message ?: "Something went wrong."
        }
    }

    private suspend fun ensureUserProfile(user: FirebaseUser, nameOverride: String? = null) {
        val reference = db.collection("users").document(user.uid)
        val name = nameOverride ?: user.displayName ?: "Seller"
        val email = user.email ?: ""

        try {
            val existing = reference.get().await()
            if (existing.exists()) {
                reference.set(mapOf("displayName" to name, "email" to email), SetOptions.merge())
                    .await()
            } else {
                reference.set(
                    mapOf(
                        "displayName" to name,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (_: Exception) {
            // Best-effort, exactly as on the web — a failed profile write
            // should never block someone from using the app.
        }
    }

    /** Port of touchPresence(). Deliberately NOT self-scheduling: see
     * PresenceHeartbeat, which only ticks while the app is actually in the
     * foreground. The onNewMessage Cloud Function skips BOTH push and email
     * when lastActiveAt is under five minutes old, so a heartbeat that keeps
     * running in the background silently suppresses every notification. The
     * web gates on document.visibilityState for the same reason. */
    suspend fun touchPresence() {
        val boundUid = _state.value.uid ?: return
        try {
            db.collection("users").document(boundUid)
                .set(mapOf("lastActiveAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .await()
        } catch (_: Exception) {
            // Presence is notification suppression, not load-bearing.
        }
    }
}
