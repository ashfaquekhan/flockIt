package com.example.flock.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private fun loadInitialState(): AuthUserState {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        val isDemo = prefs.getBoolean("is_demo_mode", true) // Default to demo/local mode for immediate usability
        val savedEmail = prefs.getString("user_email", "") ?: ""
        val savedName = prefs.getString("user_name", "") ?: ""

        return if (lastAccount != null) {
            AuthUserState(
                isSignedIn = true,
                isDemoMode = false,
                email = lastAccount.email ?: savedEmail,
                displayName = lastAccount.displayName ?: savedName.ifEmpty { "FlockIt User" },
                photoUrl = lastAccount.photoUrl?.toString(),
                accessToken = null // Refreshed via GoogleAuthUtil/token task when needed
            )
        } else if (isDemo || savedEmail.isNotEmpty()) {
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
            .requestScopes(driveFileScope, sheetsScope)
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        prefs.edit()
            .putBoolean("is_demo_mode", false)
            .putString("user_email", account.email ?: "")
            .putString("user_name", account.displayName ?: "")
            .apply()

        _authState.value = AuthUserState(
            isSignedIn = true,
            isDemoMode = false,
            email = account.email ?: "",
            displayName = account.displayName ?: "FlockIt User",
            photoUrl = account.photoUrl?.toString(),
            accessToken = account.idToken
        )
    }

    fun enableDemoMode(email: String = "farmer@flockit.local", name: String = "Local Farmer (Offline)") {
        prefs.edit()
            .putBoolean("is_demo_mode", true)
            .putString("user_email", email)
            .putString("user_name", name)
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

    fun getAuthHeader(): String? {
        val token = _authState.value.accessToken
        return if (!token.isNullOrBlank()) "Bearer $token" else null
    }
}
