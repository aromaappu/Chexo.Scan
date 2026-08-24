package com.chexo.scan.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.AuthPrefs
import com.chexo.scan.PasswordHasher
import com.chexo.scan.SettingsData
import com.chexo.scan.data.AppDatabase
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import com.chexo.scan.ui.theme.LocalAppColors

@Composable
fun SettingsScreen(username: String, onBack: () -> Unit, onLogout: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // General settings
    var printerSize by remember { mutableStateOf(SettingsData.getPrinterSize(context)) }
    var lowStockLimit by remember { mutableIntStateOf(SettingsData.getLowStockLimit(context)) }
    var theme by remember { mutableStateOf(SettingsData.getTheme(context)) }

    // Dialogs
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showLowStockDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutPage by remember { mutableStateOf(false) }
    var showPrivacyPage by remember { mutableStateOf(false) }
    var stayLoggedIn by remember { mutableStateOf(SettingsData.getStayLoggedIn(context)) }
    var backupJson by remember { mutableStateOf("") }
    var backingUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.primary)
                .padding(top = 32.dp, bottom = 12.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(modifier = Modifier.padding(16.dp)) {
            // ── Profile ──
            item {
                SectionHeader("Profile")
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(username, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                            Text("Logged in account", fontSize = 12.sp, color = colors.gray)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── General ──
            item {
                SectionHeader("General")
                Card {
                    // Bill Settings
                    var showBillDialog by remember { mutableStateOf(false) }
                    SettingRow("Bill Settings", "Printer, shop name & phone", Icons.Default.Print) {
                        showBillDialog = true
                    }
                    if (showBillDialog) {
                        BillSettingsDialog(context, colors, onDismiss = { showBillDialog = false })
                    }
                    DividerLine()
                    // Low Stock Limit
                    SettingRow("Low Stock Limit", "$lowStockLimit items", Icons.Default.Warning) {
                        showLowStockDialog = true
                    }
                    DividerLine()
                    // Theme
                    SettingRow("Theme", theme, Icons.Default.Refresh) {
                        showThemeDialog = true
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Backup & Restore ──
            item {
                SectionHeader("Backup & Restore")
                Card {
                    // Backup
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            backingUp = true
                            scope.launch {
                                try {
                                    val json = SettingsData.createBackup(context, username)
                                    backupJson = json.toString()
                                    // Save to Downloads
                                    val fileName = "ChexoScan_Backup_${username}_${System.currentTimeMillis()}.json"
                                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    val file = java.io.File(dir, fileName)
                                    file.writeText(backupJson)
                                    Toast.makeText(context, "Backup saved: $fileName", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                backingUp = false
                            }
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (backingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backup Data", fontSize = 14.sp, color = colors.onBackground)
                            Text("Save all products & sales as JSON", fontSize = 11.sp, color = colors.gray)
                        }
                    }
                    DividerLine()
                    // Restore
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showRestoreDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Restore Data", fontSize = 14.sp, color = colors.onBackground)
                            Text("Import backup (same account only)", fontSize = 11.sp, color = colors.gray)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Account ──
            item {
                SectionHeader("Account")
                Card {
                    // Change Password
                    SettingRow("Change Password", "••••••••", Icons.Default.AccountCircle) {
                        showChangePasswordDialog = true
                    }
                    DividerLine()
                    // Stay Logged In toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Stay Logged In", fontSize = 14.sp, color = colors.onBackground)
                            Text("Off = requires login on app restart", fontSize = 11.sp, color = colors.gray)
                        }
                        androidx.compose.material3.Switch(
                            checked = stayLoggedIn,
                            onCheckedChange = {
                                stayLoggedIn = it
                                SettingsData.setStayLoggedIn(context, it)
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = colors.primary
                            )
                        )
                    }
                    DividerLine()
                    // Logout
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showLogoutDialog = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Logout", fontSize = 14.sp, color = Color(0xFFFF9800))
                    }
                    DividerLine()
                    // Clear All Data
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showClearDataDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear All Data", fontSize = 14.sp, color = colors.error)
                            Text("Delete all products & sales", fontSize = 11.sp, color = colors.gray)
                        }
                    }
                    DividerLine()
                    // Delete Account
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDeleteDialog = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delete Account", fontSize = 14.sp, color = colors.error)
                            Text("Delete account & all data", fontSize = 11.sp, color = colors.gray)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── About & Privacy ──
            item {
                SectionHeader("About & Privacy")
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showAboutPage = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("About ChexoScan", fontSize = 14.sp, color = colors.onBackground)
                            Text("App info & version", fontSize = 11.sp, color = colors.gray)
                        }
                        Text("›", fontSize = 18.sp, color = colors.gray)
                    }
                    DividerLine()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showPrivacyPage = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Policy", fontSize = 14.sp, color = colors.onBackground)
                            Text("Data & privacy information", fontSize = 11.sp, color = colors.gray)
                        }
                        Text("›", fontSize = 18.sp, color = colors.gray)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── About Full Page ──
    if (showAboutPage) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.primary)
                        .padding(top = 32.dp, bottom = 12.dp, start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAboutPage = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("About ChexoScan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("ChexoScan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                                Spacer(Modifier.height(4.dp))
                                Text("Version: 1.0.0", fontSize = 13.sp, color = colors.gray)
                                Spacer(Modifier.height(16.dp))
                                Text("About ChexoScan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ChexoScan is a simple POS and stock management app made for small shop owners.",
                                    fontSize = 14.sp, color = colors.onBackground, lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Scan packed product barcodes while adding stock or making a bill. Quickly calculate the total, check available stock, identify out-of-stock products, and understand your shop's sales, profit, or loss.",
                                    fontSize = 14.sp, color = colors.onBackground, lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ChexoScan works without an internet connection for normal scanning, stock management, and billing.",
                                    fontSize = 14.sp, color = colors.onBackground, lineHeight = 20.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Made for small shop owners.", fontSize = 14.sp, color = colors.gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Privacy Full Page ──
    if (showPrivacyPage) {
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.primary)
                        .padding(top = 32.dp, bottom = 12.dp, start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showPrivacyPage = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Privacy Policy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("ChexoScan Privacy Policy", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                Spacer(Modifier.height(4.dp))
                                Text("Last Updated: August 22, 2026", fontSize = 12.sp, color = colors.gray)
                                Spacer(Modifier.height(16.dp))
                                PrivacyItem("1. Information We Collect", "ChexoScan collects your username and password for account login. Passwords are securely stored in hashed form. If you use Google verification, we store your Gmail address for account verification.")
                                PrivacyItem("2. Product Data", "Product information such as product name, price, and related product details is stored locally on your device. We do not collect this information on our servers.")
                                PrivacyItem("3. Backup and Restore", "ChexoScan allows you to create a JSON backup file and save it to your local storage. A backup can only be restored when the same account is logged in.")
                                PrivacyItem("4. Camera Permission", "ChexoScan uses your camera only for scanning product barcodes. Camera data is not collected or stored by us.")
                                PrivacyItem("5. Data Sharing", "Your personal information is not sold or shared with third parties.")
                                PrivacyItem("6. Analytics", "ChexoScan does not use analytics or tracking services to track your activity.")
                                PrivacyItem("7. Account Deletion", "You can delete your account from the app. When your account is deleted, we do not retain the deleted account data.")
                                PrivacyItem("8. Contact Us", "For support, suggestions, or privacy questions, contact us at: aromalkp0597h@gmail.com")
                                Spacer(Modifier.height(12.dp))
                                Text("By using ChexoScan, you agree to this Privacy Policy.", fontSize = 13.sp, color = colors.gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Printer Size Dialog ──
    if (showPrinterDialog) {
        val options = listOf("58mm", "80mm", "A4", "A5")
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            containerColor = colors.surface,
            title = { Text("Printer Size", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = {
                Column {
                    options.forEach { size ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                printerSize = size
                                SettingsData.setPrinterSize(context, size)
                                showPrinterDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (printerSize == size) colors.primary else colors.gray)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(size, fontSize = 15.sp, color = colors.onBackground)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ── Low Stock Limit Dialog ──
    if (showLowStockDialog) {
        var limitText by remember { mutableStateOf(lowStockLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showLowStockDialog = false },
            containerColor = colors.surface,
            title = { Text("Low Stock Limit", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = {
                Column {
                    Text("Products with quantity at or below this limit will be highlighted.", color = colors.onBackground, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                        label = { Text("Limit") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onBackground,
                            unfocusedTextColor = colors.onBackground,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.gray
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val limit = limitText.toIntOrNull() ?: 5
                    lowStockLimit = limit
                    SettingsData.setLowStockLimit(context, limit)
                    showLowStockDialog = false
                }) { Text("Save", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showLowStockDialog = false }) { Text("Cancel", color = colors.gray) }
            }
        )
    }

    // ── Theme Dialog ──
    if (showThemeDialog) {
        val options = listOf("Light", "Dark", "System")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = colors.surface,
            title = { Text("Theme", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = {
                Column {
                    options.forEach { t ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                theme = t
                                SettingsData.setTheme(context, t)
                                showThemeDialog = false
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (theme == t) colors.primary else colors.gray)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(t, fontSize = 15.sp, color = colors.onBackground)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ── Change Password Dialog ──
    if (showChangePasswordDialog) {
        var currentPw by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        var confirmPw by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onBackground, unfocusedTextColor = colors.onBackground,
            focusedBorderColor = colors.primary, unfocusedBorderColor = colors.gray,
            focusedLabelColor = colors.primary, unfocusedLabelColor = colors.gray
        )

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            containerColor = colors.surface,
            title = { Text("Change Password", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = {
                Column {
                    if (error.isNotEmpty()) {
                        Text(error, color = colors.error, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(value = currentPw, onValueChange = { currentPw = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors)
                }
            },                    confirmButton = {
                TextButton(onClick = {
                    when {
                        currentPw.isEmpty() -> error = "Enter current password"
                        newPw.length < 4 -> error = "Password must be at least 4 characters"
                        newPw != confirmPw -> error = "Passwords don't match"
                        else -> {
                            scope.launch {
                                val result = SettingsData.changePasswordSupabase(context, username, currentPw, newPw)
                                if (result == null) {
                                    Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
                                    showChangePasswordDialog = false
                                } else {
                                    error = result
                                }
                            }
                        }
                    }
                }) { Text("Change", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel", color = colors.gray) }
            }
        )
    }

    // ── Restore Dialog ──
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val json = inputStream?.bufferedReader()?.readText() ?: ""
                    inputStream?.close()
                    if (json.isEmpty()) {
                        Toast.makeText(context, "Empty file", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    // Validate username
                    val root = org.json.JSONObject(json)
                    val backupUser = root.optString("username", "")
                    if (backupUser != username) {
                        Toast.makeText(context, "This backup belongs to '$backupUser'. Cannot restore to '$username'.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val result = SettingsData.restoreBackup(context, json, username)
                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showRestoreDialog) {
        var restoring by remember { mutableStateOf(false) }
        var restoreResult by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = colors.surface,
            title = { Text("Restore Backup", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = {
                Column {
                    Text("Select a .json backup file from your device.", fontSize = 13.sp, color = colors.onBackground)
                    Spacer(Modifier.height(4.dp))
                    Text("Backup must belong to '$username'.", fontSize = 12.sp, color = colors.gray)
                    Spacer(Modifier.height(8.dp))
                    if (restoring) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = colors.primary)
                    }
                    if (restoreResult.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(restoreResult, fontSize = 13.sp, color = if (restoreResult.startsWith("Restored")) Color(0xFF388E3C) else colors.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                }) { Text("Choose File", color = colors.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel", color = colors.gray) }
            }
        )
    }

    // ── Delete Account Dialog ──
    if (showDeleteDialog) {
        var confirmStep by remember { mutableIntStateOf(1) }
        var confirmText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; confirmStep = 1; confirmText = "" },
            containerColor = colors.surface,
            title = {
                Text(
                    if (confirmStep == 1) "Delete Account?" else "Type 'DELETE' to confirm",
                    fontWeight = FontWeight.Bold,
                    color = colors.error
                )
            },
            text = {
                Column {
                    if (confirmStep == 1) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("This will permanently delete:", fontSize = 13.sp, color = colors.onBackground)
                        Text("• All products", fontSize = 13.sp, color = colors.onBackground)
                        Text("• All sales history", fontSize = 13.sp, color = colors.onBackground)
                        Text("• Account data", fontSize = 13.sp, color = colors.onBackground)
                        Spacer(Modifier.height(4.dp))
                        Text("This cannot be undone!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.error)
                    } else {
                        Text("Type DELETE below:", fontSize = 13.sp, color = colors.onBackground)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            label = { Text("Type DELETE") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onBackground, unfocusedTextColor = colors.onBackground,
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.gray
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmStep == 1) {
                        confirmStep = 2
                    } else if (confirmText == "DELETE") {
                        scope.launch {
                            SettingsData.deleteAccount(context, username)
                            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                            onLogout()
                        }
                    }
                }) {
                    Text(
                        if (confirmStep == 1) "Next" else "Delete Forever",
                        color = colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; confirmStep = 1; confirmText = "" }) {
                    Text("Cancel", color = colors.gray)
                }
            }
        )
    }

    // ── Clear All Data Dialog ──
    if (showClearDataDialog) {
        var clearStep by remember { mutableIntStateOf(1) }
        var clearText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false; clearStep = 1; clearText = "" },
            containerColor = colors.surface,
            title = {
                Text(
                    if (clearStep == 1) "Clear All Data?" else "Type 'CLEAR' to confirm",
                    fontWeight = FontWeight.Bold,
                    color = colors.error
                )
            },
            text = {
                Column {
                    if (clearStep == 1) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("This will permanently delete:", fontSize = 13.sp, color = colors.onBackground)
                        Text("• All products", fontSize = 13.sp, color = colors.onBackground)
                        Text("• All sales history", fontSize = 13.sp, color = colors.onBackground)
                        Spacer(Modifier.height(4.dp))
                        Text("Your account will remain. This cannot be undone!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.error)
                    } else {
                        Text("Type CLEAR below:", fontSize = 13.sp, color = colors.onBackground)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clearText,
                            onValueChange = { clearText = it },
                            label = { Text("Type CLEAR") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.onBackground, unfocusedTextColor = colors.onBackground,
                                focusedBorderColor = colors.primary, unfocusedBorderColor = colors.gray
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (clearStep == 1) {
                        clearStep = 2
                    } else if (clearText == "CLEAR") {
                        scope.launch {
                            SettingsData.clearAllData(context, username)
                            Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                            showClearDataDialog = false
                            clearStep = 1
                            clearText = ""
                        }
                    }
                }) {
                    Text(
                        if (clearStep == 1) "Next" else "Clear Forever",
                        color = colors.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false; clearStep = 1; clearText = "" }) {
                    Text("Cancel", color = colors.gray)
                }
            }
        )
    }

    // ── Logout Dialog ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colors.surface,
            title = { Text("Logout?", fontWeight = FontWeight.Bold, color = colors.onBackground) },
            text = { Text("Are you sure you want to logout?", fontSize = 14.sp, color = colors.onBackground) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    AuthPrefs.clearSession(context)
                    onLogout()
                }) { Text("Logout", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = colors.gray) }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val colors = LocalAppColors.current
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.gray,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
    ) { content() }
}

@Composable
private fun SettingRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = colors.onBackground, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = colors.gray)
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 18.sp, color = colors.gray)
    }
}

@Composable
private fun PrivacyItem(title: String, content: String) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(content, fontSize = 14.sp, color = colors.gray, lineHeight = 20.sp)
    }
}

@Composable
private fun DividerLine() {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.background).padding(horizontal = 12.dp))
}

@Composable
private fun BillSettingsDialog(context: Context, colors: com.chexo.scan.ui.theme.AppColors, onDismiss: () -> Unit) {
    var shopName by remember { mutableStateOf(context.getSharedPreferences("chexo_settings", Context.MODE_PRIVATE).getString("bill_shop_name", "") ?: "") }
    var phone by remember { mutableStateOf(SettingsData.getPhoneNumber(context)) }
    var showPreview by remember { mutableStateOf(false) }
    var previewSize by remember { mutableStateOf(SettingsData.getPrinterSize(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Bill Settings", color = Color.Black, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = colors.primary)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.Black, unfocusedTextColor = Color.Black, cursorColor = colors.primary)
                )
                Spacer(Modifier.height(12.dp))
                Text("Printer Size:", fontSize = 13.sp, color = Color.Black)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("58mm", "80mm", "A5", "A4").forEach { size ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (previewSize == size) colors.primary else Color(0xFFE0E0E0))
                                .clickable { previewSize = size }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(size, fontSize = 12.sp, color = if (previewSize == size) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Mini preview
                BillPreview(shopName.ifBlank { "Shop Name" }, phone, previewSize)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSharedPreferences("chexo_settings", Context.MODE_PRIVATE).edit().putString("bill_shop_name", shopName).apply()
                SettingsData.setPhoneNumber(context, phone)
                SettingsData.setPrinterSize(context, previewSize)
                onDismiss()
            }) { Text("Save", color = colors.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
private fun BillPreview(shopName: String, phone: String, printerSize: String) {
    val (w, h) = when (printerSize) {
        "58mm" -> Pair(160, 220)
        "80mm" -> Pair(200, 260)
        "A5" -> Pair(220, 300)
        else -> Pair(220, 300)
    }
    Column(
        modifier = Modifier
            .width(w.dp)
            .height(h.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(shopName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        if (phone.isNotBlank()) {
            Text("Phone: $phone", fontSize = 9.sp, color = Color.DarkGray)
        }
        Text("Date: 22 Aug 2026", fontSize = 9.sp, color = Color.DarkGray)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
        Spacer(Modifier.height(4.dp))
        Text("Item", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
        Spacer(Modifier.height(2.dp))
        Text("Product A", fontSize = 9.sp, color = Color.DarkGray)
        Text("Product B", fontSize = 9.sp, color = Color.DarkGray)
        Text("Product C", fontSize = 9.sp, color = Color.DarkGray)
        Spacer(Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray))
        Spacer(Modifier.height(4.dp))
        Text("TOTAL: Rs.350", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
    }
}
