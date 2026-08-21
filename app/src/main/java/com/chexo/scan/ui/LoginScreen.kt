package com.chexo.scan.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chexo.scan.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.mfa.FactorType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val OffWhite = Color(0xFFF5F5F5)
private val DarkBlue = Color(0xFF1976D2)
private val ErrorRed = Color(0xFFFF5252)

@Serializable
data class UserProfile(
    val id: String? = null,
    val user_id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val totp_secret: String? = null
)

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit = {}) {
    var page by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }

    var showEnrollDialog by remember { mutableStateOf(false) }
    var totpUri by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var totpError by remember { mutableStateOf("") }
    var enrolledFactorId by remember { mutableStateOf("") }

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotStep by remember { mutableIntStateOf(0) }
    var forgotInput by remember { mutableStateOf("") }
    var forgotInputError by remember { mutableStateOf("") }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotTotpSecret by remember { mutableStateOf("") }
    var forgotTotpCode by remember { mutableStateOf("") }
    var forgotNewPass by remember { mutableStateOf("") }
    var forgotConfirmPass by remember { mutableStateOf("") }
    var forgotError by remember { mutableStateOf("") }
    var forgotLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun clearErrors() {
        authError = ""; usernameError = ""; emailError = ""; passwordError = ""; confirmError = ""
    }

    fun switchPage(newPage: Int) {
        keyboardController?.hide()
        clearErrors()
        page = newPage
    }

    fun doSignupStep1() {
        clearErrors()
        if (username.isBlank()) { usernameError = "Username is required"; return }
        if (username.length < 3) { usernameError = "Min 3 characters"; return }
        if (password.isBlank()) { passwordError = "Password is required"; return }
        if (password.length < 6) { passwordError = "Min 6 characters"; return }
        if (confirmPassword.isBlank()) { confirmError = "Confirm your password"; return }
        if (confirmPassword != password) { confirmError = "Passwords do not match"; return }
        keyboardController?.hide()
        page = 2
    }

    fun doSignupStep2() {
        clearErrors()
        if (email.isBlank()) { emailError = "Email is required"; return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailError = "Enter valid email"; return }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                SupabaseClient.client.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                    ?: throw Exception("No user ID found")
                SupabaseClient.client.from("users").insert(
                    buildJsonObject { put("user_id", userId); put("username", username); put("email", email) }
                )
                val enrollResult = SupabaseClient.client.auth.mfa.enroll(
                    FactorType.TOTP, "Google Authenticator"
                ) { issuer = "ChexoScan" }
                totpUri = enrollResult.data.uri ?: enrollResult.data.qrCode
                totpSecret = enrollResult.data.secret
                enrolledFactorId = enrollResult.id
                showEnrollDialog = true
            } catch (e: Exception) {
                authError = when {
                    e.message?.contains("already", true) == true -> "Username or email already exists"
                    else -> e.message?.take(80) ?: "Signup failed"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun doLogin() {
        clearErrors()
        if (username.isBlank() || password.isBlank()) { authError = "Enter both username and password"; return }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                val profileResult = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("email")) {
                        filter { eq("username", username.trim()) }
                    }
                    .decodeList<UserProfile>()
                val loginEmail = if (profileResult.isNotEmpty()) profileResult[0].email else username
                if (loginEmail.isNullOrBlank()) { authError = "Username not found"; isLoading = false; return@launch }
                SupabaseClient.client.auth.signInWith(Email) {
                    this.email = loginEmail
                    this.password = password
                }
                Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                onLoginSuccess(username.trim())
            } catch (e: Exception) {
                authError = when {
                    e.message?.contains("Invalid login", true) == true -> "Wrong password"
                    else -> e.message?.take(80) ?: "Login failed"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun doEnrollVerify() {
        if (totpCode.length != 6) { totpError = "Enter 6-digit code"; return }
        totpError = ""
        isLoading = true
        scope.launch {
            try {
                SupabaseClient.client.auth.mfa.createChallengeAndVerify(
                    factorId = enrolledFactorId, code = totpCode
                )
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    SupabaseClient.client.from("users").update(
                        buildJsonObject { put("totp_secret", totpSecret) }
                    ) { filter { eq("user_id", userId) } }
                }
                showEnrollDialog = false
                onLoginSuccess(username.trim())
            } catch (e: Exception) {
                totpError = when {
                    e.message?.contains("Invalid", true) == true -> "Wrong code, try again"
                    else -> e.message?.take(80) ?: "Verification failed"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun doForgotLookup() {
        if (forgotInput.isBlank()) { forgotInputError = "Enter username or email"; return }
        forgotInputError = ""; forgotLoading = true; forgotError = ""
        scope.launch {
            try {
                val input = forgotInput.trim()
                val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()
                val profileResult = if (isEmail) {
                    SupabaseClient.client.from("users").select { filter { eq("email", input) } }.decodeList<UserProfile>()
                } else {
                    SupabaseClient.client.from("users").select { filter { eq("username", input) } }.decodeList<UserProfile>()
                }
                if (profileResult.isEmpty()) { forgotInputError = "User not found"; forgotLoading = false; return@launch }
                val profile = profileResult[0]
                if (profile.totp_secret.isNullOrBlank()) { forgotError = "2FA not set up"; forgotLoading = false; return@launch }
                forgotEmail = profile.email ?: ""
                forgotTotpSecret = profile.totp_secret
                forgotStep = 1
            } catch (e: Exception) {
                forgotError = e.message?.take(80) ?: "Lookup failed"
            } finally {
                forgotLoading = false
            }
        }
    }

    fun doForgotVerifyTotp() {
        if (forgotTotpCode.length != 6) { forgotError = "Enter 6-digit code"; return }
        forgotError = ""
        if (!TotpHelper.verify(forgotTotpSecret, forgotTotpCode)) { forgotError = "Wrong code, try again"; return }
        forgotStep = 2
    }

    fun doForgotReset() {
        if (forgotNewPass.isBlank()) { forgotError = "Enter new password"; return }
        if (forgotNewPass.length < 6) { forgotError = "Min 6 characters"; return }
        if (forgotNewPass != forgotConfirmPass) { forgotError = "Passwords do not match"; return }
        forgotError = ""; forgotLoading = true
        scope.launch {
            try {
                SupabaseClient.client.postgrest.rpc("reset_user_password", buildJsonObject {
                    put("user_email", forgotEmail); put("new_password", forgotNewPass)
                })
                forgotStep = 3
            } catch (e: Exception) {
                forgotError = e.message?.take(80) ?: "Reset failed"
            } finally {
                forgotLoading = false
            }
        }
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

            when (page) {
                0 -> {
                    Box(modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(DarkBlue), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(value = username, onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = usernameError.isNotEmpty())
                            if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(value = password, onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = passwordError.isNotEmpty())
                            if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it }, colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.LightGray))
                                Text("Remember Me", color = Color.White, fontSize = 11.sp)
                            }
                            Row {
                                Text("Sign Up", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { switchPage(1) })
                                Spacer(Modifier.width(16.dp))
                                Text("Forgot Password?", color = Color(0xFF90CAF9), fontSize = 13.sp, modifier = Modifier.clickable {
                                    showForgotDialog = true; forgotStep = 0; forgotInput = ""; forgotInputError = ""
                                    forgotError = ""; forgotNewPass = ""; forgotConfirmPass = ""; forgotTotpCode = ""
                                })
                            }
                        }
                    }
                }
                1 -> {
                    Box(modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(DarkBlue), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(value = username, onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = usernameError.isNotEmpty())
                            if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(value = password, onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = passwordError.isNotEmpty())
                            if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(value = confirmPassword, onValueChange = { confirmPassword = it; clearErrors() },
                                placeholder = { Text("Confirm Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = confirmError.isNotEmpty())
                            if (confirmError.isNotEmpty()) Text(confirmError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Login", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { switchPage(0) })
                        }
                    }
                }
                2 -> {
                    Box(modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(DarkBlue), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text("Enter Email", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(value = email, onValueChange = { email = it; clearErrors() },
                                placeholder = { Text("Email", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black), isError = emailError.isNotEmpty())
                            if (emailError.isNotEmpty()) Text(emailError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Back", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { switchPage(1) })
                        }
                    }
                }
            }

            if (authError.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(authError, color = ErrorRed, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { keyboardController?.hide(); when (page) { 0 -> doLogin(); 1 -> doSignupStep1(); 2 -> doSignupStep2() } },
                modifier = Modifier.width(200.dp).height(50.dp), shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue), enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                else Text(text = if (page == 0) "Login" else "Sign Up", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (showEnrollDialog) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue)) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Scan QR Code", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Open Google Authenticator and scan this QR code, then enter the 6-digit code below.", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        val qrBitmap = remember(totpUri) { try { TotpHelper.generateQrBitmap(totpUri) } catch (_: Exception) { null } }
                        if (qrBitmap != null) Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(200.dp))
                        else Text("QR generation failed", color = ErrorRed, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Or enter manually:", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Text(totpSecret, color = Color(0xFF90CAF9), fontSize = 11.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextField(value = totpCode, onValueChange = { if (it.length <= 6) totpCode = it.filter { c -> c.isDigit() }; totpError = "" },
                            placeholder = { Text("6-digit code", color = Color.Gray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black))
                        if (totpError.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(totpError, color = ErrorRed, fontSize = 12.sp) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { doEnrollVerify() }, modifier = Modifier.fillMaxWidth().height(45.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)), shape = RoundedCornerShape(25.dp), enabled = !isLoading) {
                            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Verify", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        if (showForgotDialog) {
            Dialog(onDismissRequest = { showForgotDialog = false }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = DarkBlue)) {
                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        when (forgotStep) {
                            0 -> {
                                Text("Forgot Password", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(value = forgotInput, onValueChange = { forgotInput = it; forgotInputError = "" },
                                    placeholder = { Text("Username or Email", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                    singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black))
                                if (forgotInputError.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(forgotInputError, color = ErrorRed, fontSize = 12.sp) }
                            }
                            1 -> {
                                Text("Enter TOTP Code", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Open Google Authenticator\nand enter the 6-digit code", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(value = forgotTotpCode, onValueChange = { if (it.length <= 6) forgotTotpCode = it.filter { c -> c.isDigit() }; forgotError = "" },
                                    placeholder = { Text("6-digit code", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black))
                            }
                            2 -> {
                                Text("New Password", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(value = forgotNewPass, onValueChange = { forgotNewPass = it; forgotError = "" },
                                    placeholder = { Text("New Password", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                    visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black))
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(value = forgotConfirmPass, onValueChange = { forgotConfirmPass = it; forgotError = "" },
                                    placeholder = { Text("Confirm Password", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFF0F0F0), unfocusedContainerColor = Color(0xFFF0F0F0), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                                    visualTransformation = PasswordVisualTransformation(), singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color.Black))
                            }
                            3 -> {
                                Text("Done!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Password changed successfully.\nPlease login with your new password.", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, textAlign = TextAlign.Center)
                            }
                        }
                        if (forgotError.isNotEmpty()) { Spacer(modifier = Modifier.height(8.dp)); Text(forgotError, color = ErrorRed, fontSize = 12.sp, textAlign = TextAlign.Center) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            forgotError = ""
                            when (forgotStep) { 0 -> doForgotLookup(); 1 -> doForgotVerifyTotp(); 2 -> doForgotReset(); 3 -> { showForgotDialog = false; forgotStep = 0 } }
                        }, modifier = Modifier.fillMaxWidth().height(45.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)), shape = RoundedCornerShape(25.dp), enabled = !forgotLoading) {
                            if (forgotLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(text = when (forgotStep) { 0 -> "Verify"; 1 -> "Verify Code"; 2 -> "Reset Password"; 3 -> "Back to Login"; else -> "" },
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
