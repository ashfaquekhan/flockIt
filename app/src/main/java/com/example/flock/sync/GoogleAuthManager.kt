package com.example.flock.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AuthUserState(
    val isSignedIn: Boolean = false,
    val isDemoMode: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val accessToken: String? = null
)

class GoogleAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flockit_auth_prefs", Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(loadInitialState())
    val authState: StateFlow<AuthUserState> = _authState.asStateFlow()

    private val driveFileScope = Scope("https://www.googleapis.com/auth/drive.file")
    private val sheetsScope = Scope("https://www.googleapis.com/auth/spreadsheets")
    // Hidden per-user, per-app folder that syncs across devices — holds the FlockIt index (control plane).
    private val appDataScope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val oauthScopeString =
        "oauth2:https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"

    private fun loadInitialState(): AuthUserState {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val choseDemo = prefs.getBoolean("is_demo_mode", false)
        val savedEmail = prefs.getString("user_email", "") ?: ""
        val savedName = prefs.getString("user_name", "") ?: ""
        val savedToken = prefs.getString("user_access_token", null)

        return if (lastAccount != null) {
            AuthUserState(
                isSignedIn = true,
                isDemoMode = false,
                email = lastAccount.email ?: savedEmail,
                displayName = lastAccount.displayName ?: savedName.ifEmpty { "FlockIt User" },
                photoUrl = lastAccount.photoUrl?.toString(),
                accessToken = savedToken
            )
        } else if (choseDemo) {
            AuthUserState(
                isSignedIn = true,
                isDemoMode = true,
                email = savedEmail.ifEmpty { "farmer@flockit.local" },
                displayName = savedName.ifEmpty { "Local Farmer (Offline)" },
                photoUrl = null,
                accessToken = null
            )
        } else {
            AuthUserState(
                isSignedIn = false,
                isDemoMode = false
            )
        }
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(driveFileScope, sheetsScope, appDataScope)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        val email = account.email ?: ""
        val displayName = account.displayName ?: "FlockIt User"
        val photo = account.photoUrl?.toString()

        prefs.edit()
            .putBoolean("is_demo_mode", false)
            .putString("user_email", email)
            .putString("user_name", displayName)
            .apply()

        _authState.value = AuthUserState(
            isSignedIn = true,
            isDemoMode = false,
            email = email,
            displayName = displayName,
            photoUrl = photo,
            accessToken = prefs.getString("user_access_token", null)
        )
    }

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val email = _authState.value.email.ifBlank {
            GoogleSignIn.getLastSignedInAccount(context)?.email ?: ""
        }
        if (email.isBlank() || _authState.value.isDemoMode) return@withContext null

        try {
            val token = GoogleAuthUtil.getToken(context, email, oauthScopeString)
            prefs.edit().putString("user_access_token", token).putLong("user_access_token_at", System.currentTimeMillis()).apply()
            _authState.value = _authState.value.copy(accessToken = token)
            token
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Failed to retrieve OAuth access token for $email", e)
            null
        }
    }

    fun enableDemoMode(email: String = "farmer@flockit.local", name: String = "Local Farmer (Offline)") {
        prefs.edit()
            .putBoolean("is_demo_mode", true)
            .putString("user_email", email)
            .putString("user_name", name)
            .remove("user_access_token")
            .apply()

        _authState.value = AuthUserState(
            isSignedIn = true,
            isDemoMode = true,
            email = email,
            displayName = name,
            photoUrl = null,
            accessToken = null
        )
    }

    fun signOut(onComplete: () -> Unit = {}) {
        prefs.edit().clear().apply()
        getGoogleSignInClient().signOut().addOnCompleteListener {
            _authState.value = AuthUserState(
                isSignedIn = false,
                isDemoMode = false
            )
            onComplete()
        }
    }

    /**
     * The header for a Google request. A Google access token lasts an hour: one kept from an earlier session, or
     * older than [TOKEN_FRESH_MS], is swapped for a fresh one first — a stale token made every load on the flock
     * pages fail quietly (v47).
     */
    suspend fun getAuthHeader(): String? {
        var token = _authState.value.accessToken
        val signedIn = _authState.value.isSignedIn && !_authState.value.isDemoMode
        val age = System.currentTimeMillis() - prefs.getLong("user_access_token_at", 0L)
        if (signedIn && (token.isNullOrBlank() || age > TOKEN_FRESH_MS)) {
            if (!token.isNullOrBlank()) invalidate(token)
            token = refreshAccessToken() ?: token.takeIf { age < TOKEN_FRESH_MS }
        }
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }

    /** Drops a token Google no longer takes (expired or revoked), so the next request fetches a new one. */
    suspend fun invalidate(token: String? = _authState.value.accessToken) = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext
        try { GoogleAuthUtil.clearToken(context, token) } catch (e: Exception) { Log.w("GoogleAuthManager", "clearToken: ${e.message}") }
        prefs.edit().remove("user_access_token").putLong("user_access_token_at", 0L).apply()
        _authState.value = _authState.value.copy(accessToken = null)
    }

    companion object {
        /** a token is used for 45 minutes, then renewed (Google's last an hour) */
        const val TOKEN_FRESH_MS = 45 * 60 * 1000L
    }
}
