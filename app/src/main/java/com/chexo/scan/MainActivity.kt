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
import com.chexo.scan.ui.theme.ChexoScaTheme
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var loggedIn by mutableStateOf(false)
    private var username by mutableStateOf("User")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                if (session != null) {
                    val user = SupabaseClient.client.auth.currentUserOrNull()
                    username = user?.userMetadata?.get("username")?.toString() ?: "User"
                    loggedIn = true
                }
            } catch (e: Exception) {
                Log.e("ChexoScan", "Session restore failed", e)
            }
        }

        handleAuthIntent(intent)

        setContent {
            ChexoScaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    if (loggedIn) {
                        HomeScreen(username = username, onLogout = {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    SupabaseClient.client.auth.signOut()
                                    loggedIn = false
                                    username = "User"
                                } catch (e: Exception) {
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Logout failed.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
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
        handleAuthIntent(intent)
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
