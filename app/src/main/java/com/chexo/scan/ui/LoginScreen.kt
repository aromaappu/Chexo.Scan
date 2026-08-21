package com.chexo.scan.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chexo.scan.AuthPrefs
import com.chexo.scan.PasswordHasher
import com.chexo.scan.SupabaseClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
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
    val password: String? = null,
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

    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotStep by remember { mutableIntStateOf(0) }
    var forgotInput by remember { mutableStateOf("") }
    var forgotInputError by remember { mutableStateOf("") }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotConfirmEmail by remember { mutableStateOf("") }
    var forgotUsername by remember { mutableStateOf("") }
    var forgotNewPass by remember { mutableStateOf("") }
    var forgotConfirmPass by remember { mutableStateOf("") }
    var forgotError by remember { mutableStateOf("") }
    var forgotLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            if (showForgotDialog && forgotStep == 1) {
                forgotError = "Google sign-in cancelled"
            } else {
                emailError = "Google sign-in cancelled"
            }
            return@rememberLauncherForActivityResult
        }
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val picked = account?.email
            if (picked.isNullOrBlank()) {
                if (showForgotDialog && forgotStep == 1) {
                    forgotError = "No email returned from Google"
                } else {
                    emailError = "No email returned from Google"
                }
                return@rememberLauncherForActivityResult
            }
            if (showForgotDialog && forgotStep == 1) {
                forgotConfirmEmail = picked
                forgotError = ""
            } else {
                email = picked
                emailError = ""
                authError = ""
            }
        } catch (e: ApiException) {
            val msg = "Google sign-in failed (${e.statusCode})"
            if (showForgotDialog && forgotStep == 1) forgotError = msg
            else emailError = msg
        }
    }

    fun openGoogleSignIn() {
        // Sign out first so Google always shows the account chooser
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    fun clearErrors() {
        authError = ""; usernameError = ""; emailError = ""; passwordError = ""; confirmError = ""
    }

    fun formatDbError(e: Exception): String {
        val raw = listOfNotNull(e.message, e.cause?.message)
            .joinToString(" | ")
            .ifBlank { e.toString() }
        return when {
            raw.contains("users_username", true) ||
                (raw.contains("duplicate", true) && raw.contains("username", true)) ->
                "This username is already registered. Please choose another or log in."
            raw.contains("users_email", true) ||
                (raw.contains("duplicate", true) && raw.contains("email", true)) ->
                "This Gmail is already registered. Please use a different account or log in."
            raw.contains("duplicate", true) || raw.contains("unique", true) ->
                "An account with these details already exists. Please log in instead."
            raw.contains("row-level security", true) || raw.contains("RLS", true) ->
                "Blocked by RLS. Allow INSERT/SELECT on public.users for anon"
            raw.contains("null value", true) && raw.contains("user_id", true) ->
                "user_id is required. Run: ALTER TABLE users ALTER COLUMN user_id DROP NOT NULL;"
            raw.contains("Could not find the", true) && raw.contains("column", true) ->
                "Column missing or schema cache. In Supabase: Settings → API → Reload schema"
            else -> raw.take(160)
        }
    }

    fun switchPage(newPage: Int) {
        keyboardController?.hide()
        clearErrors()
        if (newPage == 2) {
            email = ""
        }
        page = newPage
    }

    fun finishLogin(name: String) {
        if (rememberMe) AuthPrefs.saveSession(context, name)
        else AuthPrefs.clearSession(context)
        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
        onLoginSuccess(name)
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
        switchPage(2)
    }

    fun doSignupStep2() {
        clearErrors()
        if (email.isBlank()) { emailError = "Continue with Google first"; return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Continue with Google first"
            return
        }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                val existingUser = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "email")) {
                        filter { eq("username", username.trim()) }
                    }
                    .decodeList<UserProfile>()
                if (existingUser.isNotEmpty()) {
                    authError = "This username is already registered. Please choose another or log in."
                    return@launch
                }
                val existingEmail = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "email")) {
                        filter { eq("email", email.trim()) }
                    }
                    .decodeList<UserProfile>()
                if (existingEmail.isNotEmpty()) {
                    authError = "This Gmail is already registered. Please use a different account or log in."
                    return@launch
                }

                SupabaseClient.client.from("users").insert(
                    buildJsonObject {
                        put("username", username.trim())
                        put("email", email.trim())
                        put("password", PasswordHasher.hash(password))
                    }
                )
                finishLogin(username.trim())
            } catch (e: Exception) {
                authError = formatDbError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun doLogin() {
        clearErrors()
        if (username.isBlank() || password.isBlank()) {
            authError = "Please enter your username and password to continue."
            return
        }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                val profiles = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "email", "password")) {
                        filter { eq("username", username.trim()) }
                    }
                    .decodeList<UserProfile>()
                if (profiles.isEmpty()) {
                    authError = "No account found with this username. Please sign up to create one."
                    return@launch
                }
                val hash = profiles[0].password
                if (hash.isNullOrBlank()) {
                    authError = "This account is incomplete. Please sign up again to finish setup."
                    return@launch
                }
                if (!PasswordHasher.matches(password, hash)) {
                    authError = "Incorrect password. Please try again, or use Forgot Password."
                    return@launch
                }
                finishLogin(username.trim())
            } catch (e: Exception) {
                authError = formatDbError(e)
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
                if (profileResult.isEmpty()) {
                    forgotInputError = "User not found"
                    forgotLoading = false
                    return@launch
                }
                val profile = profileResult[0]
                forgotEmail = profile.email ?: ""
                forgotUsername = profile.username ?: ""
                forgotConfirmEmail = ""
                forgotStep = 1
            } catch (e: Exception) {
                forgotError = e.message?.take(80) ?: "Lookup failed"
            } finally {
                forgotLoading = false
            }
        }
    }

    fun doForgotConfirmEmail() {
        if (forgotConfirmEmail.isBlank()) { forgotError = "Continue with Google first"; return }
        if (!forgotConfirmEmail.equals(forgotEmail, ignoreCase = true)) {
            forgotError = "Email does not match account"
            return
        }
        forgotError = ""
        forgotStep = 2
    }

    fun doForgotReset() {
        if (forgotNewPass.isBlank()) { forgotError = "Enter new password"; return }
        if (forgotNewPass.length < 6) { forgotError = "Min 6 characters"; return }
        if (forgotNewPass != forgotConfirmPass) { forgotError = "Passwords do not match"; return }
        forgotError = ""; forgotLoading = true
        scope.launch {
            try {
                val filterColumn = if (forgotUsername.isNotBlank()) "username" else "email"
                val filterValue = if (forgotUsername.isNotBlank()) forgotUsername else forgotEmail
                SupabaseClient.client.from("users").update(
                    buildJsonObject { put("password", PasswordHasher.hash(forgotNewPass)) }
                ) {
                    filter { eq(filterColumn, filterValue) }
                }
                forgotStep = 3
            } catch (e: Exception) {
                forgotError = formatDbError(e)
            } finally {
                forgotLoading = false
            }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color(0xFFF0F0F0),
        unfocusedContainerColor = Color(0xFFF0F0F0),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

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
                    Box(
                        modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(DarkBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = username,
                                onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                                isError = usernameError.isNotEmpty()
                            )
                            if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                                isError = passwordError.isNotEmpty()
                            )
                            if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { rememberMe = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.White,
                                        uncheckedColor = Color.LightGray
                                    )
                                )
                                Text("Remember Me", color = Color.White, fontSize = 11.sp)
                            }
                            Row {
                                Text(
                                    "Sign Up",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { switchPage(1) }
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    "Forgot Password?",
                                    color = Color(0xFF90CAF9),
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable {
                                        showForgotDialog = true
                                        forgotStep = 0
                                        forgotInput = ""
                                        forgotInputError = ""
                                        forgotError = ""
                                        forgotNewPass = ""
                                        forgotConfirmPass = ""
                                        forgotConfirmEmail = ""
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Box(
                        modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(DarkBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = username,
                                onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Username", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                                isError = usernameError.isNotEmpty()
                            )
                            if (usernameError.isNotEmpty()) Text(usernameError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                                isError = passwordError.isNotEmpty()
                            )
                            if (passwordError.isNotEmpty()) Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; clearErrors() },
                                placeholder = { Text("Confirm Password", color = Color.Gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                                isError = confirmError.isNotEmpty()
                            )
                            if (confirmError.isNotEmpty()) Text(confirmError, color = ErrorRed, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Login",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { switchPage(0) }
                            )
                        }
                    }
                }
                2 -> {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(150.dp))
                            .background(DarkBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text(
                                "Verify Google",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Open Google Sign-In and choose your Gmail from Google",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { openGoogleSignIn() },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Text(
                                    "Continue with Google",
                                    color = DarkBlue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (email.isNotBlank()) {
                                Text(
                                    email,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    "Not verified yet",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            if (emailError.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(emailError, color = ErrorRed, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Back",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { switchPage(1) }
                            )
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
                onClick = {
                    keyboardController?.hide()
                    when (page) {
                        0 -> doLogin()
                        1 -> doSignupStep1()
                        2 -> doSignupStep2()
                    }
                },
                modifier = Modifier.width(200.dp).height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = if (page == 0) "Login" else "Sign Up",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        if (showForgotDialog) {
            Dialog(
                onDismissRequest = { showForgotDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (forgotStep) {
                            0 -> {
                                Text("Forgot Password", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(
                                    value = forgotInput,
                                    onValueChange = { forgotInput = it; forgotInputError = "" },
                                    placeholder = { Text("Username or Email", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = fieldColors,
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                                )
                                if (forgotInputError.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(forgotInputError, color = ErrorRed, fontSize = 12.sp)
                                }
                            }
                            1 -> {
                                Text("Confirm with Google", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Use Google Sign-In to verify the Gmail linked to this account",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { openGoogleSignIn() },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Text(
                                        "Continue with Google",
                                        color = DarkBlue,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (forgotConfirmEmail.isBlank()) "Not verified yet" else forgotConfirmEmail,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            2 -> {
                                Text("New Password", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                TextField(
                                    value = forgotNewPass,
                                    onValueChange = { forgotNewPass = it; forgotError = "" },
                                    placeholder = { Text("New Password", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = fieldColors,
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = forgotConfirmPass,
                                    onValueChange = { forgotConfirmPass = it; forgotError = "" },
                                    placeholder = { Text("Confirm Password", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                    colors = fieldColors,
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                                )
                            }
                            3 -> {
                                Text("Done!", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Password changed successfully.\nPlease login with your new password.",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        if (forgotError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(forgotError, color = ErrorRed, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                forgotError = ""
                                when (forgotStep) {
                                    0 -> doForgotLookup()
                                    1 -> doForgotConfirmEmail()
                                    2 -> doForgotReset()
                                    3 -> {
                                        showForgotDialog = false
                                        forgotStep = 0
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(45.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            shape = RoundedCornerShape(25.dp),
                            enabled = !forgotLoading
                        ) {
                            if (forgotLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = when (forgotStep) {
                                        0 -> "Next"
                                        1 -> "Confirm Email"
                                        2 -> "Reset Password"
                                        3 -> "Back to Login"
                                        else -> ""
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
