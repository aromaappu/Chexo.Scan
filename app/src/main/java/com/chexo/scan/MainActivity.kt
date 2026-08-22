package com.chexo.scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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

class MainActivity : ComponentActivity() {

    private var loggedIn by mutableStateOf(false)
    private var username by mutableStateOf("User")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (AuthPrefs.isLoggedIn(this) && SettingsData.getStayLoggedIn(this)) {
            loggedIn = true
            username = AuthPrefs.getCurrentUsername(this)
        } else {
            AuthPrefs.clearSession(this)
        }

        setContent {
            ChexoScaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    if (loggedIn) {
                        HomeScreen(
                            username = username,
                            onLogout = {
                                AuthPrefs.clearSession(this@MainActivity)
                                loggedIn = false
                                username = "User"
                            }
                        )
                    } else {
                        LoginScreen(
                            onLoginSuccess = { name ->
                                username = name
                                loggedIn = true
                            }
                        )
                    }
                }
            }
        }
    }
}
