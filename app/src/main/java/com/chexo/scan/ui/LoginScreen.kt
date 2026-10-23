package com.chexo.scan.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chexo.scan.AccountApi
import com.chexo.scan.AuthPrefs
import com.chexo.scan.BuildConfig
import com.chexo.scan.SettingsData
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String? = null,
    val user_id: String? = null,
    val username: String? = null,
    val email: String? = null
)

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shopName by remember { mutableStateOf("") }
    var localPassword by remember { mutableStateOf("") }
    var selectedToken by remember { mutableStateOf<String?>(null) }
    var selectedEmail by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val options = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
    }
    val client = remember { GoogleSignIn.getClient(context, options) }

    fun finishLogin(name: String, email: String) {
        AuthPrefs.saveSession(context, name)
        AuthPrefs.saveAccount(context, name, email)
        Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
        onLoginSuccess(name)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            error = "Google sign-in cancelled."
        } else {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val token = account.idToken
                if (token.isNullOrBlank()) {
                    error = "Google ID token unavailable. Check your web OAuth client configuration."
                } else {
                    selectedToken = token
                    selectedEmail = account.email.orEmpty()
                    busy = true
                    scope.launch {
                        try {
                            val found = AccountApi.lookup(token)
                            if (found == null) {
                                isRegistering = true
                                error = "New Google account. Choose a shop name to register."
                            } else {
                                isRegistering = false
                                if (SettingsData.getStayLoggedIn(context)) {
                                    finishLogin(found.username, found.email)
                                } else {
                                    // Google verification occurs on each manual login.
                                    finishLogin(found.username, found.email)
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Unable to verify Google sign-in."
                        } finally { busy = false }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Google sign-in failed."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ChexoScan", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Secure Google sign-in · Shop data stays on this device")
        Spacer(Modifier.height(24.dp))
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            Text("Google OAuth setup required: add GOOGLE_WEB_CLIENT_ID to local.properties.",
                color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            enabled = !busy && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
            onClick = {
                error = ""
                // Google account picker lets the user choose a different shop account.
                client.signOut().addOnCompleteListener { launcher.launch(client.signInIntent) }
            }
        ) { Text("Continue with Google") }
        if (isRegistering && selectedToken != null) {
            Spacer(Modifier.height(18.dp))
            Text("Register shop for $selectedEmail")
            OutlinedTextField(
                value = shopName, onValueChange = { shopName = it },
                label = { Text("Shop name") }, singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy && shopName.trim().length >= 3, onClick = {
                busy = true
                scope.launch {
                    try {
                        val registered = AccountApi.register(selectedToken!!, shopName.trim())
                        finishLogin(registered.username, registered.email)
                    } catch (e: Exception) {
                        error = e.message ?: "Signup failed."
                    } finally { busy = false }
                }
            }) { Text("Create account") }
        }
        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}
