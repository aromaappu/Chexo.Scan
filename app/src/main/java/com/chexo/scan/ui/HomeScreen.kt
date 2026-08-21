package com.chexo.scan.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.data.AppDatabase
import com.chexo.scan.data.LocalProduct
import kotlinx.coroutines.launch

private val DarkBlue = Color(0xFF1976D2)
private val OffWhite = Color(0xFFF5F5F5)
private val IconGray = Color(0xFF888888)
private val ErrorRed = Color(0xFFFF5252)

@Composable
fun HomeScreen(username: String = "User", onLogout: () -> Unit = {}) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBlue)
                .padding(top = 32.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ChexoScan",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Welcome, $username",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Logout",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        when (selectedTab) {
            0 -> SellPage(username = username, modifier = Modifier.weight(1f))
            1 -> AddPage(username = username, modifier = Modifier.weight(1f))
            2 -> AnalysisPage(username = username, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FooterIcon(Icons.Default.ShoppingCart, "Sell", selectedTab == 0) { selectedTab = 0 }
            Spacer(Modifier.width(40.dp))
            FooterIcon(Icons.Default.AddCircle, "Add", selectedTab == 1) { selectedTab = 1 }
            Spacer(Modifier.width(40.dp))
            FooterIcon(Icons.Default.BarChart, "Analysis", selectedTab == 2) { selectedTab = 2 }
        }
    }
}

@Composable
private fun SellPage(username: String, modifier: Modifier = Modifier) {
    var products by remember { mutableStateOf<List<LocalProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    LaunchedEffect(username) {
        loading = true
        try {
            products = db.localProductDao().getAllForUser(username)
        } catch (_: Exception) {}
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Scanned Products", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            products.isEmpty() -> Text("No products yet. Scan a barcode in Add tab.", color = IconGray, fontSize = 13.sp)
            else -> {
                LazyColumn {
                    items(products, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontSize = 14.sp, color = Color.Black)
                                Text("Barcode: ${product.barcode} | Qty: ${product.quantity}", fontSize = 11.sp, color = IconGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Customer: Rs.${product.customerPrice}", fontSize = 12.sp, color = DarkBlue)
                                Text("Our: Rs.${product.ourPrice}", fontSize = 12.sp, color = IconGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPage(username: String, modifier: Modifier = Modifier) {
    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var existingProducts by remember { mutableStateOf<List<LocalProduct>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
            Spacer(Modifier.height(6.dp))
            Text("Saved under account: $username", fontSize = 13.sp, color = IconGray)
            Spacer(Modifier.height(12.dp))

            BarcodeScannerView(
                onBarcodeScanned = { scannedBarcode = it }
            )
            Spacer(Modifier.height(12.dp))
            Text("— Or enter manually —", fontSize = 13.sp, color = IconGray)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

        TextField(
            value = barcode,
            onValueChange = { barcode = it; error = "" },
            placeholder = { Text("Barcode", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            colors = fieldColors,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = name,
            onValueChange = { name = it; error = "" },
            placeholder = { Text("Product name", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            colors = fieldColors,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = price,
            onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
            placeholder = { Text("Price", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
        )

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = ErrorRed, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (barcode.isBlank() || name.isBlank() || price.isBlank()) {
                    error = "Please fill barcode, name, and price."
                    return@Button
                }
                val priceValue = price.toDoubleOrNull()
                if (priceValue == null || priceValue < 0) {
                    error = "Enter a valid price."
                    return@Button
                }
                saving = true
                error = ""
                scope.launch {
                    try {
                        db.localProductDao().insert(
                            com.chexo.scan.data.LocalProduct(
                                username = username,
                                barcode = barcode.trim(),
                                name = name.trim(),
                                quantity = 1,
                                customerPrice = priceValue,
                                ourPrice = priceValue
                            )
                        )
                        barcode = ""
                        name = ""
                        price = ""
                        Toast.makeText(context, "Saved for $username only", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        error = e.message?.take(140) ?: "Save failed"
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
            shape = RoundedCornerShape(24.dp),
            enabled = !saving
        ) {
            if (saving) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text("Save Product", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        }
    }

    if (scannedBarcode != null) {
        ScanResultDialog(
            barcode = scannedBarcode!!,
            onDismiss = { scannedBarcode = null },
            onConfirm = { productName, quantity, customerPrice, ourPrice ->
                scope.launch {
                    try {
                        db.localProductDao().insert(
                            LocalProduct(
                                username = username,
                                barcode = scannedBarcode ?: "",
                                name = productName,
                                quantity = quantity,
                                customerPrice = customerPrice,
                                ourPrice = ourPrice
                            )
                        )
                        Toast.makeText(context, "Product saved!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    scannedBarcode = null
                }
            }
        )
    }
}

@Composable
private fun AnalysisPage(username: String, modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    LaunchedEffect(username) {
        loading = true
        try {
            count = db.localProductDao().getAllForUser(username).size
        } catch (_: Exception) {}
        loading = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
            Spacer(Modifier.height(8.dp))
            Text("Account: $username", fontSize = 13.sp, color = IconGray)
            Spacer(Modifier.height(16.dp))
            if (loading) CircularProgressIndicator(color = DarkBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            else Text("$count products in your account", fontSize = 16.sp, color = Color.Black)
        }
    }
}

@Composable
private fun FooterIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                if (selected) DarkBlue.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) DarkBlue else IconGray,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) DarkBlue else IconGray)
    }
}
