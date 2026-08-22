package com.chexo.scan.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chexo.scan.ui.theme.LocalAppColors

@Serializable
data class UserProfile(
    val id: String? = null,
    val user_id: String? = null,
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    val totp_secret: String? = null
)

@Serializable
data class PasswordUpdate(
    val password: String
)

private fun formatDbError(e: Exception): String {
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
            "Blocked by RLS. Allow INSERT/SELECT/UPDATE on public.users for anon"
        raw.contains("null value", true) && raw.contains("user_id", true) ->
            "user_id is required. Run: ALTER TABLE users ALTER COLUMN user_id DROP NOT NULL;"
        raw.contains("Could not find the", true) && raw.contains("column", true) ->
            "Column missing or schema cache. In Supabase: Settings → API → Reload schema"
        else -> raw.take(160)
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit = {}) {
    val colors = LocalAppColors.current
    // 0 login | 1 signup | 2 signup google | 3 forgot google | 4 forgot new password
    var page by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var confirmError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }

    var forgotEmail by remember { mutableStateOf("") }
    var forgotUsername by remember { mutableStateOf("") }
    var forgotNewPass by remember { mutableStateOf("") }
    var forgotConfirmPass by remember { mutableStateOf("") }

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
            authError = "Google sign-in cancelled. Please try again."
            return@rememberLauncherForActivityResult
        }
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val picked = account?.email?.trim()?.lowercase()
            if (picked.isNullOrBlank()) {
                authError = "No email returned from Google"
                return@rememberLauncherForActivityResult
            }

            if (page == 3) {
                isLoading = true
                authError = ""
                scope.launch {
                    try {
                        val all = SupabaseClient.client.from("users")
                            .select(columns = Columns.list("username", "email"))
                            .decodeList<UserProfile>()
                        val profiles = all.filter {
                            it.email?.trim()?.equals(picked, ignoreCase = true) == true
                        }
                        if (profiles.isEmpty()) {
                            authError = "No account is linked to this Gmail. Please sign up first."
                        } else {
                            forgotEmail = profiles[0].email?.trim()?.lowercase() ?: picked
                            forgotUsername = profiles[0].username?.trim().orEmpty()
                            forgotNewPass = ""
                            forgotConfirmPass = ""
                            page = 4
                            authError = ""
                        }
                    } catch (e: Exception) {
                        authError = formatDbError(e)
                    } finally {
                        isLoading = false
                    }
                }
            } else if (page == 0) {
                isLoading = true
                authError = ""
                scope.launch {
                    try {
                        val all = SupabaseClient.client.from("users")
                            .select(columns = Columns.list("username", "email"))
                            .decodeList<UserProfile>()
                        val profiles = all.filter {
                            it.email?.trim()?.equals(picked, ignoreCase = true) == true
                        }
                        if (profiles.isEmpty()) {
                            authError = "No account found with this Google. Please sign up first."
                        } else {
                            val name = profiles[0].username?.trim() ?: picked
                            AuthPrefs.saveSession(context, name)
                            AuthPrefs.saveAccount(context, name, picked)
                            Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(name)
                        }
                    } catch (e: Exception) {
                        authError = formatDbError(e)
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                email = picked
                emailError = ""
                authError = ""
            }
        } catch (e: ApiException) {
            authError = "Google sign-in failed (${e.statusCode})"
        }
    }

    fun openGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    fun clearErrors() {
        authError = ""
        usernameError = ""
        emailError = ""
        passwordError = ""
        confirmError = ""
    }

    fun switchPage(newPage: Int) {
        keyboardController?.hide()
        clearErrors()
        if (newPage == 2) email = ""
        if (newPage == 3) {
            forgotEmail = ""
            forgotUsername = ""
            forgotNewPass = ""
            forgotConfirmPass = ""
        }
        page = newPage
    }

    fun finishLogin(name: String) {
        AuthPrefs.saveSession(context, name)
        AuthPrefs.saveAccount(context, name, email)
        Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
        onLoginSuccess(name)
    }

    fun doSignupStep1() {
        clearErrors()
        if (username.isBlank()) { usernameError = "Shop name is required"; return }
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
        val mail = email.trim().lowercase()
        if (mail.isBlank()) { emailError = "Continue with Google first"; return }
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
                    authError = "This shop name is already registered. Please choose another or log in."
                    return@launch
                }
                val all = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "email"))
                    .decodeList<UserProfile>()
                if (all.any { it.email?.equals(mail, ignoreCase = true) == true }) {
                    authError = "This Gmail is already registered. Please use a different account or log in."
                    return@launch
                }

                SupabaseClient.client.from("users").insert(
                    buildJsonObject {
                        put("username", username.trim())
                        put("email", mail)
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
        val loginId = username.trim()
        val loginPass = password
        if (loginId.isBlank() || loginPass.isBlank()) {
            authError = "Please enter your username and password to continue."
            return
        }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                val all = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "email", "password"))
                    .decodeList<UserProfile>()
                val profiles = all.filter {
                    it.username?.equals(loginId, ignoreCase = true) == true ||
                        it.email?.equals(loginId, ignoreCase = true) == true
                }
                if (profiles.isEmpty()) {
                    authError = "No account found. Please sign up to create one."
                    return@launch
                }
                val profile = profiles[0]
                val hash = profile.password
                if (hash.isNullOrBlank()) {
                    authError = "This account is incomplete. Please sign up again to finish setup."
                    return@launch
                }
                if (!PasswordHasher.matches(loginPass, hash)) {
                    authError = "Incorrect password. Please try again, or use Forgot Password."
                    return@launch
                }
                finishLogin(profile.username?.trim() ?: loginId)
            } catch (e: Exception) {
                authError = formatDbError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun doForgotReset() {
        clearErrors()
        if (forgotNewPass.isBlank()) { authError = "Please enter a new password."; return }
        if (forgotNewPass.length < 6) { authError = "Password must be at least 6 characters."; return }
        if (forgotNewPass != forgotConfirmPass) { authError = "Passwords do not match."; return }
        if (forgotUsername.isBlank()) {
            authError = "Google verification required."
            return
        }
        isLoading = true
        keyboardController?.hide()
        scope.launch {
            try {
                val oldProfile = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("password")) {
                        filter { eq("username", forgotUsername) }
                    }
                    .decodeList<UserProfile>()
                val oldHash = oldProfile.firstOrNull()?.password
                if (!oldHash.isNullOrBlank() && PasswordHasher.matches(forgotNewPass, oldHash)) {
                    authError = "This password is already in use. Please choose a different password."
                    isLoading = false
                    return@launch
                }

                val newHash = PasswordHasher.hash(forgotNewPass)
                SupabaseClient.client.from("users").update(
                    PasswordUpdate(password = newHash)
                ) {
                    filter { eq("username", forgotUsername) }
                }

                val verify = SupabaseClient.client.from("users")
                    .select(columns = Columns.list("username", "password")) {
                        filter { eq("username", forgotUsername) }
                    }
                    .decodeList<UserProfile>()
                val saved = verify.firstOrNull()?.password
                if (saved.isNullOrBlank() || !PasswordHasher.matches(forgotNewPass, saved)) {
                    // Fallback update by email if username update did not stick
                    SupabaseClient.client.from("users").update(
                        PasswordUpdate(password = newHash)
                    ) {
                        filter { eq("email", forgotEmail) }
                    }
                    val verify2 = SupabaseClient.client.from("users")
                        .select(columns = Columns.list("username", "password")) {
                            filter { eq("email", forgotEmail) }
                        }
                        .decodeList<UserProfile>()
                    val saved2 = verify2.firstOrNull()?.password
                    if (saved2.isNullOrBlank() || !PasswordHasher.matches(forgotNewPass, saved2)) {
                        authError = "Password update failed. Check users UPDATE policy in Supabase."
                        return@launch
                    }
                }

                username = forgotUsername
                password = ""
                confirmPassword = ""
                Toast.makeText(context, "Password updated. Please log in.", Toast.LENGTH_SHORT).show()
                switchPage(0)
                authError = ""
            } catch (e: Exception) {
                authError = formatDbError(e)
            } finally {
                isLoading = false
            }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.surfaceVariant,
        unfocusedContainerColor = colors.surfaceVariant,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Box(
        modifier = Modifier.fillMaxSize().background(colors.background).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ChexoScan", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier.size(300.dp).clip(RoundedCornerShape(150.dp)).background(colors.primary),
                contentAlignment = Alignment.Center
            ) {
                when (page) {
                    0 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Login", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = username,
                                onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Shop Name or Email", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground),
                                isError = usernameError.isNotEmpty()
                            )
                            if (usernameError.isNotEmpty()) Text(usernameError, color = colors.error, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground),
                                isError = passwordError.isNotEmpty()
                            )
                            if (passwordError.isNotEmpty()) Text(passwordError, color = colors.error, fontSize = 10.sp)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Forgot Password?",
                                    color = colors.link,
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable { switchPage(3) }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(colors.surface)
                                        .clickable {
                                            clearErrors()
                                            openGoogleSignIn()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "G",
                                        color = colors.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Sign Up",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { switchPage(1) }
                            )
                        }
                    }
                    1 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Sign Up", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = username,
                                onValueChange = { username = it; clearErrors() },
                                placeholder = { Text("Shop Name", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground),
                                isError = usernameError.isNotEmpty()
                            )
                            if (usernameError.isNotEmpty()) Text(usernameError, color = colors.error, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = password,
                                onValueChange = { password = it; clearErrors() },
                                placeholder = { Text("Password", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground),
                                isError = passwordError.isNotEmpty()
                            )
                            if (passwordError.isNotEmpty()) Text(passwordError, color = colors.error, fontSize = 10.sp)
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; clearErrors() },
                                placeholder = { Text("Confirm Password", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground),
                                isError = confirmError.isNotEmpty()
                            )
                            if (confirmError.isNotEmpty()) Text(confirmError, color = colors.error, fontSize = 10.sp)
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
                    2 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text("Verify Google", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Open Google Sign-In and choose your Gmail",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { openGoogleSignIn() },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surface),
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                Text("Continue with Google", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (email.isNotBlank()) email else "Not verified yet",
                                color = Color.White.copy(alpha = if (email.isNotBlank()) 1f else 0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            if (emailError.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(emailError, color = colors.error, fontSize = 11.sp, textAlign = TextAlign.Center)
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
                    3 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text("Forgot Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Select your Google account to reset password",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    clearErrors()
                                    openGoogleSignIn()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.surface),
                                shape = RoundedCornerShape(22.dp),
                                enabled = !isLoading
                            ) {
                                Text("Continue with Google", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Login",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { switchPage(0) }
                            )
                        }
                    }
                    4 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("New Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                forgotUsername.ifBlank { forgotEmail },
                                color = colors.link,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            TextField(
                                value = forgotNewPass,
                                onValueChange = { forgotNewPass = it; clearErrors() },
                                placeholder = { Text("New Password", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                            )
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = forgotConfirmPass,
                                onValueChange = { forgotConfirmPass = it; clearErrors() },
                                placeholder = { Text("Confirm Password", color = colors.gray, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Back",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { switchPage(3) }
                            )
                        }
                    }
                }
            }

            if (authError.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    authError,
                    color = colors.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(Modifier.height(20.dp))

            if (page != 3) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        when (page) {
                            0 -> doLogin()
                            1 -> doSignupStep1()
                            2 -> doSignupStep2()
                            4 -> doForgotReset()
                        }
                    },
                    modifier = Modifier.width(200.dp).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
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
                            text = when (page) {
                                0 -> "Login"
                                4 -> "Change Password"
                                else -> "Sign Up"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (isLoading) {
                CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}
