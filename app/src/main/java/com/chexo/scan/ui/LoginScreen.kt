package com.chexo.scan.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private val OffWhite = Color(0xFFF5F5F5)
private val DarkBlue = Color(0xFF1976D2)
private val ErrorRed = Color(0xFFFF5252)

@Serializable
data class UserRow(
    val email: String,
    val password: String
)

private fun isValidEmail(email: String): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

private fun isValidPassword(password: String): Boolean {
    return password.length >= 6
}

@Composable
fun LoginScreen() {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun validate(): Boolean {
        var valid = true
        authError = ""
        if (email.isBlank()) {
            emailError = "Email is required"
            valid = false
        } else if (!isValidEmail(email)) {
            emailError = "Enter valid email"
            valid = false
        } else {
            emailError = ""
        }
        if (password.isBlank()) {
            passwordError = "Password is required"
            valid = false
        } else if (!isValidPassword(password)) {
            passwordError = "Min 6 characters"
            valid = false
        } else {
            passwordError = ""
        }
        return valid
    }

    fun onButtonClick() {
        if (!validate()) return
        isLoading = true
        scope.launch {
            try {
                if (isLogin) {
                    SupabaseClient.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    Toast.makeText(context, "Login Successful", Toast.LENGTH_SHORT).show()
                } else {
                    SupabaseClient.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    SupabaseClient.client.from("users").insert(
                        UserRow(email = email, password = password)
                    )
                    Toast.makeText(context, "Signup Successful", Toast.LENGTH_SHORT).show()
                }
                authError = ""
            } catch (e: Exception) {
                authError = e.message ?: "Something went wrong"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ChexoScan",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBlue,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(150.dp))
                    .background(DarkBlue),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (isLogin) "Login" else "Sign Up",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))

                    TextField(
                        value = email,
                        onValueChange = { email = it; emailError = ""; authError = "" },
                        placeholder = { Text("Email", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F0F0),
                            unfocusedContainerColor = Color(0xFFF0F0F0),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorContainerColor = Color(0xFFF0F0F0),
                            errorIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                        isError = emailError.isNotEmpty()
                    )
                    if (emailError.isNotEmpty()) {
                        Text(emailError, color = ErrorRed, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(8.dp))

                    TextField(
                        value = password,
                        onValueChange = { password = it; passwordError = ""; authError = "" },
                        placeholder = { Text("Password", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF0F0F0),
                            unfocusedContainerColor = Color(0xFFF0F0F0),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorContainerColor = Color(0xFFF0F0F0),
                            errorIndicatorColor = Color.Transparent
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                        isError = passwordError.isNotEmpty()
                    )
                    if (passwordError.isNotEmpty()) {
                        Text(passwordError, color = ErrorRed, fontSize = 10.sp)
                    }
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

                    Text(
                        text = if (isLogin) "Sign Up" else "Login",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            isLogin = !isLogin
                            emailError = ""
                            passwordError = ""
                            authError = ""
                        }
                    )
                }
            }

            if (authError.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(authError, color = ErrorRed, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onButtonClick() },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp),
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
                        text = if (isLogin) "Login" else "Sign Up",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
