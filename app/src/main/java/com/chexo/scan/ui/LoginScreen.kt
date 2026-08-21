package com.chexo.scan.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.SupabaseClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val OffWhite = Color(0xFFF5F5F5)
private val DarkBlue = Color(0xFF1976D2)
private val ErrorRed = Color(0xFFFF5252)

private fun isValidEmail(email: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

private fun isValidPassword(password: String): Boolean = password.length >= 6

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit = {}) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var confirmError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            
            if (idToken != null) {
                scope.launch {
                    try {
                        if (isLogin) {
                            SupabaseClient.client.auth.signInWith(Google, idToken)
                            Toast.makeText(context, "Google Sign-In Successful", Toast.LENGTH_SHORT).show()
                            onLoginSuccess()
                        } else {
                            // Google verification successful, now create Supabase account with email/password
                            SupabaseClient.client.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                                this.data = buildJsonObject {
                                    put("username", username)
                                }
                            }
                            Toast.makeText(context, "Signup Successful. Now login with your username and password.", Toast.LENGTH_LONG).show()
                            username = ""
                            email = ""
                            password = ""
                            confirmPassword = ""
                            isLogin = true
                        }
                    } catch (e: Exception) {
                        authError = "Google authentication failed. Please try again."
                        isLoading = false
                    }
                }
            }
        } catch (e: Exception) {
            authError = "Google Sign-In failed. Please try again."
            isLoading = false
        }
    }

    fun handleGoogleSignUp() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("137431347755-8oupj75defh0l1fbm79mq1bki850ih56.apps.googleusercontent.com")
            .requestEmail()
            .build()
        
        val googleSignInClient = GoogleSignIn.getClient(context, gso)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    val rotation by animateFloatAsState(
        targetValue = if (isLogin) 0f else 180f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "rot"
    )

    val loginAlpha = (1f - rotation / 90f).coerceIn(0f, 1f)
    val signupAlpha = ((rotation - 90f) / 90f).coerceIn(0f, 1f)

    fun validate(): Boolean {
        var valid = true
        authError = ""
        usernameError = ""
        passwordError = ""
        emailError = ""
        confirmError = ""
        
        if (isLogin) {
            if (username.isBlank()) {
                usernameError = "Username is required"
                valid = false
            }
            if (password.isBlank()) {
                passwordError = "Password is required"
                valid = false
            } else if (!isValidPassword(password)) {
                passwordError = "Min 6 characters"
                valid = false
            }
        } else {
            if (username.isBlank()) {
                usernameError = "Username is required"
                valid = false
            }
            if (email.isBlank()) {
                emailError = "Email is required"
                valid = false
            } else if (!isValidEmail(email)) {
                emailError = "Enter valid email"
                valid = false
            }
            if (password.isBlank()) {
                passwordError = "Password is required"
                valid = false
            } else if (!isValidPassword(password)) {
                passwordError = "Min 6 characters"
                valid = false
            }
            if (confirmPassword.isBlank()) {
                confirmError = "Please confirm password"
                valid = false
            } else if (password != confirmPassword) {
                confirmError = "Passwords do not match"
                valid = false
            }
        }
        
        return valid
    }

    fun onButtonClick() {
        focusRequester.freeFocus()
        keyboardController?.hide()
        if (!validate()) return
        isLoading = true
        scope.launch {
            kotlinx.coroutines.delay(100)
            try {
                if (isLogin) {
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = username
                        this.password = password
                    }
                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                    authError = ""
                    username = ""
                    password = ""
                    onLoginSuccess()
                } else {
                    handleGoogleSignUp()
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("Invalid login credentials") == true -> "Username or password is incorrect"
                    e.message?.contains("User already registered") == true -> "Username already registered. Please login instead."
                    e.message?.contains("Email not confirmed") == true -> "Please confirm your email first"
                    e.message?.contains("weak password") == true -> "Password is too weak"
                    e.message?.contains("rate limit") == true -> "Server busy. Please try again later."
                    e.message?.contains("too many requests") == true -> "Server busy. Please try again later."
                    e.message?.contains("quota") == true -> "Server busy. Please try again later."
                    e.message?.contains("timeout") == true -> "Server busy. Please try again later."
                    e.message?.contains("overloaded") == true -> "Server busy. Please try again later."
                    else -> "Authentication failed. Please try again."
                }
                authError = errorMessage
                isLoading = false
            }
        }
    }

    fun onSignUpClick() {
        if (!isLogin) return
        keyboardController?.hide()
        isLogin = false
        authError = ""
        usernameError = ""
        passwordError = ""
        emailError = ""
        confirmError = ""
    }

    fun onLoginClick() {
        if (isLogin) return
        keyboardController?.hide()
        isLogin = true
        authError = ""
        usernameError = ""
        passwordError = ""
        emailError = ""
        confirmError = ""
    }

    Box(
        modifier = Modifier.fillMaxSize().background(OffWhite).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ChexoScan", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 40f * density
                    }
                    .clip(RoundedCornerShape(150.dp))
                    .background(DarkBlue),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer { alpha = loginAlpha }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = username, onValueChange = { username = it; usernameError = ""; authError = "" },
                            placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = usernameError.isNotEmpty()
                        )
                        if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = password, onValueChange = { password = it; passwordError = ""; authError = "" },
                            placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = passwordError.isNotEmpty()
                        )
                        if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.LightGray))
                            Text("Remember Me", color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Sign Up", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onSignUpClick() })
                    }
                }

                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .graphicsLayer { alpha = signupAlpha }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = username, onValueChange = { username = it; usernameError = ""; authError = "" },
                            placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = usernameError.isNotEmpty()
                        )
                        if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = email, onValueChange = { email = it; emailError = ""; authError = "" },
                            placeholder = { Text("Email", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = emailError.isNotEmpty()
                        )
                        if (emailError.isNotEmpty()) Text(emailError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = password, onValueChange = { password = it; passwordError = ""; authError = "" },
                            placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = passwordError.isNotEmpty()
                        )
                        if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = confirmPassword, onValueChange = { confirmPassword = it; confirmError = ""; authError = "" },
                            placeholder = { Text("Confirm Password", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0),
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                            isError = confirmError.isNotEmpty()
                        )
                        if (confirmError.isNotEmpty()) Text(confirmError, color = ErrorRed, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.LightGray))
                            Text("Remember Me", color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Login", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onLoginClick() })
                    }
                }
            }

            if (authError.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(authError, color = ErrorRed, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))

            if (isLogin) {
                Button(
                    onClick = { 
                        onButtonClick() 
                    },
                    modifier = Modifier.width(200.dp).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Login",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            } else {
                Button(
                    onClick = { 
                        onButtonClick() 
                    },
                    modifier = Modifier.width(200.dp).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBlue)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Sign Up with Google",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }
            }
        }
    }
}
