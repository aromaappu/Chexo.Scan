package com.chexo.scan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chexo.scan.ui.HomeScreen
import com.chexo.scan.ui.LoginScreen
import com.chexo.scan.ui.UserProfile
import com.chexo.scan.ui.theme.ChexoScaTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var loggedIn by mutableStateOf(false)
    private var username by mutableStateOf("User")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inventory is device-local. Persist only a shop name in private preferences;
        // never use a stale Supabase Auth session as proof of identity.
        if (SettingsData.getStayLoggedIn(this) && AuthPrefs.isLoggedIn(this)) {
            val savedUsername = AuthPrefs.getCurrentUsername(this)
            if (savedUsername.isNotBlank()) {
                username = savedUsername
                loggedIn = true
            }
        }

        setContent {
            ChexoScaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    if (loggedIn) {
                        HomeScreen(username = username, onLogout = {
                            AuthPrefs.clearSession(this@MainActivity)
                            com.chexo.scan.ui.BillState.items.value = emptyList()
                            loggedIn = false
                            username = "User"
                        })
                    } else {
                        LoginScreen(
                            onLoginSuccess = { name ->
                                loggedIn = true
                                username = name
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        val data = intent.data
        if (data == null || data.scheme != "chexoscan" || data.host != "auth-callback") {
            return
        }

        SupabaseClient.client.handleDeeplinks(
            intent = intent,
            onSessionSuccess = {
                runOnUiThread {
                    loggedIn = true
                    Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { e ->
                Log.e("ChexoScan", "Auth callback failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
