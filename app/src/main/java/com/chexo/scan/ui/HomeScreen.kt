package com.chexo.scan.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.SettingsData
import com.chexo.scan.ui.theme.LocalAppColors
import com.chexo.scan.data.AppDatabase
import com.chexo.scan.data.LocalProduct
import com.chexo.scan.data.Sale
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Persistent bill items (survives tab switching)
object BillState {
    var items = mutableStateOf<List<LocalProduct>>(emptyList())
}

@Composable
fun HomeScreen(username: String = "User", onLogout: () -> Unit = {}) {
    val colors = LocalAppColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(username = username, onBack = { showSettings = false }, onLogout = onLogout)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.primary)
                .padding(top = 32.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("ChexoScan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Welcome, $username", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(24.dp))
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
                .background(colors.surface)
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

// ─────────────────────────────────────────────────────────
//  SELL PAGE — Scanner, lookup, bill list, PDF, Print
// ─────────────────────────────────────────────────────────
@Composable
private fun SellPage(username: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    val billItemsState = BillState.items
    var billItems by billItemsState
    var notFoundBarcode by remember { mutableStateOf<String?>(null) }
    var generatingBill by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        // Scanner
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sell Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            Spacer(Modifier.height(4.dp))
            Text("Scan barcode to add to bill", fontSize = 13.sp, color = colors.gray)
            Spacer(Modifier.height(10.dp))
            BarcodeScannerView(onBarcodeScanned = { scannedBarcode = it })
        }

        // Bill items list
        if (billItems.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bill Items (${billItems.size})",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary
                )
                androidx.compose.material3.IconButton(
                    onClick = { billItems = emptyList() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = colors.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(billItems, key = { it.id.toString() + "_" + it.timestamp }) { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 14.sp, color = colors.onBackground, fontWeight = FontWeight.Medium)
                            Text("Barcode: ${product.barcode}", fontSize = 11.sp, color = colors.gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Minus button
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    if (product.quantity > 1) {
                                        billItems = billItems.toMutableList().apply {
                                            val idx = indexOfFirst { it.id == product.id && it.timestamp == product.timestamp }
                                            if (idx >= 0) this[idx] = product.copy(quantity = product.quantity - 1)
                                        }
                                    } else {
                                        billItems = billItems.filter { it.id != product.id || it.timestamp != product.timestamp }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            }
                            // Quantity
                            Text("${product.quantity}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary, modifier = Modifier.padding(horizontal = 4.dp))
                            // Plus button
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    billItems = billItems.toMutableList().apply {
                                        val idx = indexOfFirst { it.id == product.id && it.timestamp == product.timestamp }
                                        if (idx >= 0) this[idx] = product.copy(quantity = product.quantity + 1)
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                            }
                            // Amount
                            Text("Rs.${product.customerPrice * product.quantity}", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                            // Delete button
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    billItems = billItems.filter { it.id != product.id || it.timestamp != product.timestamp }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = colors.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Total + buttons
            val total = billItems.sumOf { it.customerPrice * it.quantity }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Total: Rs.${"%.2f".format(total)}",
                    fontSize = 14.sp, color = colors.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            generatingBill = true
                            scope.launch {
                                try {
                                    generatePdf(context, billItems, username)
                                    reduceQuantities(db, billItems, username)
                                    billItems = emptyList()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                generatingBill = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        enabled = !generatingBill
                    ) {
                        if (generatingBill) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("PDF", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            generatingBill = true
                            scope.launch {
                                try {
                                    printBill(context, billItems, username)
                                    reduceQuantities(db, billItems, username)
                                    billItems = emptyList()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                generatingBill = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        enabled = !generatingBill
                    ) {
                        if (generatingBill) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Print", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            generatingBill = true
                            scope.launch {
                                try {
                                    reduceQuantities(db, billItems, username)
                                    Toast.makeText(context, "Bill saved!", Toast.LENGTH_SHORT).show()
                                    billItems = emptyList()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                generatingBill = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        enabled = !generatingBill
                    ) {
                        if (generatingBill) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Bill", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Scan a barcode to start selling", color = colors.gray, fontSize = 13.sp)
            }
        }
    }

    // Handle scan - lookup in Add section DB
    if (scannedBarcode != null) {
        LaunchedEffect(scannedBarcode) {
            val barcode = scannedBarcode ?: return@LaunchedEffect
            val found = db.localProductDao().getByBarcodeForUser(barcode, username)
            if (found != null) {
                if (found.quantity <= 0) {
                    Toast.makeText(context, "Out of stock: ${found.name}", Toast.LENGTH_SHORT).show()
                } else {
                    val existingIdx = billItems.indexOfFirst { it.barcode == barcode }
                    if (existingIdx >= 0) {
                        val existing = billItems[existingIdx]
                        billItems = billItems.toMutableList().apply {
                            this[existingIdx] = existing.copy(quantity = existing.quantity + 1)
                        }
                    } else {
                        billItems = billItems + found.copy(quantity = 1)
                    }
                    Toast.makeText(context, "Added: ${found.name}", Toast.LENGTH_SHORT).show()
                }
            } else {
                notFoundBarcode = barcode
                Toast.makeText(context, "Product not found. Add it first.", Toast.LENGTH_SHORT).show()
            }
            scannedBarcode = null
        }
    }

    // Show not-found dialog
    if (notFoundBarcode != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notFoundBarcode = null },
            title = { Text("Not Found") },
            text = { Text("Barcode '$notFoundBarcode' not found. Please add this product in the Add tab first.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { notFoundBarcode = null }) {
                    Text("OK")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────
//  ADD PAGE — Scanner + list of added products
// ─────────────────────────────────────────────────────────
@Composable
private fun AddPage(username: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<LocalProduct>>(emptyList()) }
    var editingProduct by remember { mutableStateOf<LocalProduct?>(null) }
    var duplicateProduct by remember { mutableStateOf<LocalProduct?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    // Refresh product list
    fun refreshProducts() {
        scope.launch {
            try { products = db.localProductDao().getAllForUser(username) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(username) { refreshProducts() }

    Column(modifier = modifier.fillMaxWidth().imePadding()) {
        // Title bar
        Text(
            "Add Product",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Scanner area - fixed at top
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Shop: $username", fontSize = 11.sp, color = colors.gray)
            Spacer(Modifier.height(8.dp))
            BarcodeScannerView(onBarcodeScanned = { scannedBarcode = it })
        }

        // Search bar - fixed below scanner
        if (products.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search product name or barcode...", fontSize = 13.sp, color = colors.gray) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onBackground,
                    unfocusedTextColor = colors.onBackground,
                    cursorColor = colors.primary,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.gray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }

        // Filter products based on search
        val filteredProducts = if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.barcode.contains(searchQuery, ignoreCase = true)
            }
        }

        // Product list - scrollable below search
        if (products.isNotEmpty()) {
            Text(
                "Added Products (${filteredProducts.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(filteredProducts, key = { it.id }) { product ->
                    val isOutOfStock = product.quantity <= 0
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOutOfStock) colors.warning else colors.surface)
                            .clickable { editingProduct = product }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 14.sp, color = colors.onBackground, fontWeight = FontWeight.Medium)
                            Text("Barcode: ${product.barcode}", fontSize = 11.sp, color = colors.gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isOutOfStock) {
                                        Text("⚠️", fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        "Qty: ${product.quantity}",
                                        fontSize = 12.sp,
                                        color = if (isOutOfStock) colors.warningText else colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("C: Rs.${product.customerPrice} | O: Rs.${product.ourPrice}", fontSize = 10.sp, color = colors.gray)
                            }
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            db.localProductDao().deleteById(product.id)
                                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                            refreshProducts()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Scan dialog — check if already added
    if (scannedBarcode != null && editingProduct == null && duplicateProduct == null) {
        LaunchedEffect(scannedBarcode) {
            val barcode = scannedBarcode ?: return@LaunchedEffect
            val existing = db.localProductDao().getByBarcodeForUser(barcode, username)
            if (existing != null) {
                duplicateProduct = existing
            } else {
                // New product — show empty dialog
            }
        }
        // Only show new-product dialog if no duplicate found
        if (duplicateProduct == null && scannedBarcode != null) {
            ScanResultDialog(
                barcode = scannedBarcode!!,
                onDismiss = { scannedBarcode = null },
                onConfirm = { productName, qty, cp, op ->
                    scope.launch {
                        try {
                            db.localProductDao().insert(
                                LocalProduct(
                                    username = username,
                                    barcode = scannedBarcode ?: "",
                                    name = productName,
                                    quantity = qty,
                                    customerPrice = cp,
                                    ourPrice = op
                                )
                            )
                            Toast.makeText(context, "Product saved!", Toast.LENGTH_SHORT).show()
                            refreshProducts()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        scannedBarcode = null
                    }
                }
            )
        }
    }

    // Already Added dialog
    if (duplicateProduct != null) {
        val p = duplicateProduct!!
        ScanResultDialog(
            barcode = p.barcode,
            onDismiss = { duplicateProduct = null; scannedBarcode = null },
            onConfirm = { productName, qty, cp, op ->
                scope.launch {
                    try {
                        db.localProductDao().update(
                            p.copy(name = productName, quantity = qty, customerPrice = cp, ourPrice = op)
                        )
                        Toast.makeText(context, "Product updated!", Toast.LENGTH_SHORT).show()
                        refreshProducts()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    duplicateProduct = null
                    scannedBarcode = null
                }
            },
            editName = p.name,
            editQuantity = p.quantity,
            editCustomerPrice = p.customerPrice,
            editOurPrice = p.ourPrice,
            isAlreadyAdded = true,
            onAddQuantity = { addQty ->
                scope.launch {
                    try {
                        db.localProductDao().update(
                            p.copy(quantity = p.quantity + addQty)
                        )
                        Toast.makeText(context, "Qty +$addQty for ${p.name}", Toast.LENGTH_SHORT).show()
                        refreshProducts()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    duplicateProduct = null
                    scannedBarcode = null
                }
            }
        )
    }

    // Edit dialog
    if (editingProduct != null) {
        val p = editingProduct!!
        ScanResultDialog(
            barcode = p.barcode,
            onDismiss = { editingProduct = null },
            onConfirm = { productName, qty, cp, op ->
                scope.launch {
                    try {
                        db.localProductDao().update(
                            p.copy(
                                name = productName,
                                quantity = qty,
                                customerPrice = cp,
                                ourPrice = op
                            )
                        )
                        Toast.makeText(context, "Product updated!", Toast.LENGTH_SHORT).show()
                        refreshProducts()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    editingProduct = null
                }
            },
            editName = p.name,
            editQuantity = p.quantity,
            editCustomerPrice = p.customerPrice,
            editOurPrice = p.ourPrice,
            isEdit = true,
            onDelete = {
                scope.launch {
                    try {
                        db.localProductDao().deleteById(p.id)
                        Toast.makeText(context, "Product deleted!", Toast.LENGTH_SHORT).show()
                        refreshProducts()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    editingProduct = null
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────
//  ANALYSIS PAGE — Stats, Chart, Filters
// ─────────────────────────────────────────────────────────
@Composable
private fun AnalysisPage(username: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Stats
    var todaySales by remember { mutableStateOf(0.0) }
    var todayProfit by remember { mutableStateOf(0.0) }
    var todayProductsSold by remember { mutableIntStateOf(0) }
    var lowStockCount by remember { mutableIntStateOf(0) }
    var outOfStockCount by remember { mutableIntStateOf(0) }

    // Chart data
    var filterMode by remember { mutableStateOf("day") } // day, month, year
    var selectedProductName by remember { mutableStateOf<String?>(null) }
    var productNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var chartData by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var chartMaxVal by remember { mutableStateOf<Double>(1.0) }
    var totalProducts by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf("All") }
    var selectedMonth by remember { mutableStateOf("All") }
    var selectedYear by remember { mutableStateOf("All") }

    // Load stats once on page open
    fun loadStats() {
        scope.launch {
            loading = true
            try {
                val cal = Calendar.getInstance(TimeZone.getDefault())
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis

                todaySales = db.saleDao().getTodaySales(username, startOfDay)
                todayProfit = db.saleDao().getTodayProfit(username, startOfDay)
                todayProductsSold = db.saleDao().getTodayProductsSold(username, startOfDay)

                val products = db.localProductDao().getAllForUser(username)
                totalProducts = products.size
                val lowLimit = SettingsData.getLowStockLimit(context)
                lowStockCount = products.count { it.quantity in 1..lowLimit }
                outOfStockCount = products.count { it.quantity <= 0 }

                productNames = db.saleDao().getDistinctProductNames(username)

                // Fixed chart scale from all sales
                val allSales = db.saleDao().getAllForUser(username)
                val groupedAll = allSales.groupBy {
                    val c = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
                    c.timeInMillis = it.soldAt
                    "${c.get(java.util.Calendar.DAY_OF_MONTH)}/${c.get(java.util.Calendar.MONTH) + 1}"
                }.map { (_, items) -> items.sumOf { it.totalAmount } }
                chartMaxVal = (groupedAll.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            } catch (_: Exception) {}
            loading = false
        }
    }

    // Load chart only when filter changes
    fun loadChart() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance(TimeZone.getDefault())

                val allSales = if (selectedProductName != null) {
                    db.saleDao().getSalesByProductBetween(username, selectedProductName!!, 0, now)
                } else {
                    db.saleDao().getAllForUser(username)
                }

                // Filter by day/month/year selection
                val filtered = allSales.filter { sale ->
                    val c = Calendar.getInstance(TimeZone.getDefault())
                    c.timeInMillis = sale.soldAt
                    val dayMatch = selectedDay == "All" || c.get(Calendar.DAY_OF_MONTH).toString() == selectedDay
                    val monthNames = listOf("All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val monthIdx = monthNames.indexOf(selectedMonth)
                    val monthMatch = selectedMonth == "All" || c.get(Calendar.MONTH) == monthIdx - 1
                    val yearMatch = selectedYear == "All" || c.get(Calendar.YEAR).toString() == selectedYear
                    dayMatch && monthMatch && yearMatch
                }

                // Update product names to only show sold products in selected period
                productNames = filtered.map { it.productName }.distinct().sorted()
                // Reset product filter if selected product not in filtered results
                if (selectedProductName != null && selectedProductName !in productNames) {
                    selectedProductName = null
                }

                chartData = when (filterMode) {
                    "day" -> {
                        filtered.groupBy {
                            val c = Calendar.getInstance(TimeZone.getDefault())
                            c.timeInMillis = it.soldAt
                            c.get(Calendar.DAY_OF_MONTH).toString()
                        }.map { (label, items) ->
                            label to items.sumOf { it.totalAmount }
                        }.sortedBy { it.first.toIntOrNull() ?: 0 }
                    }
                    "month" -> {
                        val monthNamesShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                        filtered.groupBy {
                            val c = Calendar.getInstance(TimeZone.getDefault())
                            c.timeInMillis = it.soldAt
                            monthNamesShort[c.get(Calendar.MONTH)]
                        }.map { (label, items) ->
                            label to items.sumOf { it.totalAmount }
                        }.sortedBy { monthNamesShort.indexOf(it.first) }
                    }
                    else -> {
                        filtered.groupBy {
                            val c = Calendar.getInstance(TimeZone.getDefault())
                            c.timeInMillis = it.soldAt
                            c.get(Calendar.YEAR).toString()
                        }.map { (label, items) ->
                            label to items.sumOf { it.totalAmount }
                        }.sortedBy { it.first.toIntOrNull() ?: 0 }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Load stats once on page open
    LaunchedEffect(username) { loadStats() }
    // Load chart when filters change
    LaunchedEffect(filterMode, selectedProductName, selectedDay, selectedMonth, selectedYear) { loadChart() }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(40.dp))
        // Header
        Text("Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        Text("Shop: $username", fontSize = 12.sp, color = colors.gray)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // ── Stats Cards ──
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Today's Sales", "Rs.${"%.0f".format(todaySales)}", colors.primary, Modifier.weight(1f))
                        StatCard("Today's Profit", "Rs.${"%.0f".format(todayProfit)}", Color(0xFF388E3C), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Sold Today", "$todayProductsSold", Color(0xFF7B1FA2), Modifier.weight(1f))
                        StatCard("Total Products", "$totalProducts", Color(0xFF0277BD), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Low Stock (≤${SettingsData.getLowStockLimit(context)})", "$lowStockCount", Color(0xFFFF9800), Modifier.weight(1f))
                        StatCard("Out of Stock", "$outOfStockCount", colors.error, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Filters ──
                item {
                    Text("Sales Chart", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    Spacer(Modifier.height(8.dp))

                    val dayList = listOf("All") + (1..31).map { it.toString() }
                    val monthList = listOf("All", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val yearList = listOf("All") + (2020..Calendar.getInstance().get(Calendar.YEAR)).map { it.toString() }.reversed().toList()

                    var dayExpanded by remember { mutableStateOf(false) }
                    var monthExpanded by remember { mutableStateOf(false) }
                    var yearExpanded by remember { mutableStateOf(false) }

                    // Day / Month / Year buttons with dropdowns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Day button + dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            val dayLabel = if (selectedDay == "All") "Day" else selectedDay
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary)
                                    .clickable { dayExpanded = true }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(dayLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            }
                            DropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }, modifier = Modifier.heightIn(max = 200.dp).background(colors.surface)) {
                                dayList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, fontSize = 13.sp, color = if (item == selectedDay) Color.White else colors.onBackground, fontWeight = if (item == selectedDay) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().background(if (item == selectedDay) colors.primary else Color.Transparent).padding(4.dp)) },
                                        onClick = { selectedDay = item; dayExpanded = false }
                                    )
                                }
                            }
                        }

                        // Month button + dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            val monthLabel = if (selectedMonth == "All") "Month" else selectedMonth
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary)
                                    .clickable { monthExpanded = true }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(monthLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            }
                            DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }, modifier = Modifier.heightIn(max = 200.dp).background(colors.surface)) {
                                monthList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, fontSize = 13.sp, color = if (item == selectedMonth) Color.White else colors.onBackground, fontWeight = if (item == selectedMonth) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().background(if (item == selectedMonth) colors.primary else Color.Transparent).padding(4.dp)) },
                                        onClick = { selectedMonth = item; monthExpanded = false }
                                    )
                                }
                            }
                        }

                        // Year button + dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            val yearLabel = if (selectedYear == "All") "Year" else selectedYear
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.primary)
                                    .clickable { yearExpanded = true }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(yearLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            }
                            DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }, modifier = Modifier.heightIn(max = 200.dp).background(colors.surface)) {
                                yearList.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item, fontSize = 13.sp, color = if (item == selectedYear) Color.White else colors.onBackground, fontWeight = if (item == selectedYear) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().background(if (item == selectedYear) colors.primary else Color.Transparent).padding(4.dp)) },
                                        onClick = { selectedYear = item; yearExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Product dropdown
                    if (productNames.isNotEmpty()) {
                        var productExpanded by remember { mutableStateOf(false) }
                        val currentProductLabel = selectedProductName ?: "All Products"

                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = currentProductLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Filter by Product", fontSize = 12.sp) },
                                trailingIcon = {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier.fillMaxWidth().clickable { productExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.onBackground,
                                    unfocusedTextColor = colors.onBackground,
                                    focusedBorderColor = colors.primary,
                                    unfocusedBorderColor = colors.gray
                                )
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp).clickable { productExpanded = true }
                            )
                            DropdownMenu(
                                expanded = productExpanded,
                                onDismissRequest = { productExpanded = false },
                                modifier = Modifier.heightIn(max = 220.dp).background(colors.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All Products", fontSize = 14.sp, color = if (selectedProductName == null) Color.White else colors.onBackground, fontWeight = if (selectedProductName == null) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().background(if (selectedProductName == null) colors.primary else Color.Transparent).padding(4.dp)) },
                                    onClick = { selectedProductName = null; productExpanded = false }
                                )
                                productNames.forEach { name ->
                                    val isSel = selectedProductName == name
                                    DropdownMenuItem(
                                        text = { Text(name, fontSize = 14.sp, color = if (isSel) Color.White else colors.onBackground, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().background(if (isSel) colors.primary else Color.Transparent).padding(4.dp)) },
                                        onClick = { selectedProductName = name; productExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Bar Chart ──
                item {
                    if (chartData.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No sales data yet", color = colors.gray, fontSize = 13.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                SalesBarChart(
                                    data = chartData.takeLast(15).reversed(),
                                    modifier = Modifier.widthIn(min = 300.dp)
                                        .width((chartData.takeLast(15).size * 50 + 24).dp)
                                        .padding(horizontal = 8.dp),
                                    fixedMax = chartMaxVal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .padding(12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = colors.gray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SalesBarChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier, fixedMax: Double = 1.0) {
    val colors = LocalAppColors.current
    val maxVal = fixedMax.coerceAtLeast(1.0)
    val barColor = colors.primary
    val labelColor = colors.gray

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { (label, value) ->
                val barHeight = (value / maxVal * 140).dp.coerceAtLeast(6.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(bottom = 4.dp)
                ) {
                    if (value > 0) {
                        Text(
                            "${"%.0f".format(value)}",
                            fontSize = 9.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
            }
        }
        // Labels row at bottom — always visible
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (label, _) ->
                Text(
                    label,
                    fontSize = 9.sp,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  FOOTER ICON
// ─────────────────────────────────────────────────────────
@Composable
private fun FooterIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .background(if (selected) colors.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) colors.primary else colors.gray, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) colors.primary else colors.gray)
    }
}

// ─────────────────────────────────────────────────────────
//  REDUCE QUANTITIES after bill
// ─────────────────────────────────────────────────────────
private suspend fun reduceQuantities(db: com.chexo.scan.data.AppDatabase, billItems: List<LocalProduct>, username: String) {
    val sales = mutableListOf<Sale>()
    for (item in billItems) {
        val existing = db.localProductDao().getById(item.id) ?: continue
        val soldQty = minOf(item.quantity, existing.quantity)
        val newQty = existing.quantity - soldQty
        db.localProductDao().update(existing.copy(quantity = maxOf(newQty, 0)))
        sales.add(
            Sale(
                username = username,
                barcode = item.barcode,
                productName = item.name,
                quantitySold = soldQty,
                unitPrice = item.customerPrice,
                ourPrice = item.ourPrice,
                totalAmount = item.customerPrice * soldQty
            )
        )
    }
    if (sales.isNotEmpty()) {
        db.saleDao().insertAll(sales)
    }
}

// ─────────────────────────────────────────────────────────
//  PDF GENERATION
// ─────────────────────────────────────────────────────────
private fun generatePdf(context: Context, products: List<LocalProduct>, username: String) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = android.graphics.Paint().apply {
            textSize = 20f; isFakeBoldText = true; color = android.graphics.Color.parseColor("#1976D2")
        }
        val headerPaint = android.graphics.Paint().apply {
            textSize = 12f; isFakeBoldText = true; color = android.graphics.Color.BLACK
        }
        val textPaint = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.DKGRAY }
        val linePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY; strokeWidth = 1f
        }

        var y = 50f
        canvas.drawText("ChexoScan - Bill", 40f, y, titlePaint)
        y += 25f
        canvas.drawText("Shop: $username", 40f, y, textPaint)
        y += 15f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Date: $dateStr", 40f, y, textPaint)
        y += 25f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f

        // Headers
        canvas.drawText("Item", 40f, y, headerPaint)
        canvas.drawText("Rate", 300f, y, headerPaint)
        canvas.drawText("Amount", 430f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 18f

        var total = 0.0
        for (p in products) {
            val amount = p.customerPrice * p.quantity
            total += amount
            canvas.drawText(p.name.take(30), 40f, y, textPaint)
            canvas.drawText("Rs.${p.customerPrice}", 300f, y, textPaint)
            canvas.drawText("Rs.${"%.2f".format(amount)}", 430f, y, textPaint)
            y += 20f
        }

        y += 5f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f
        val totalPaint = android.graphics.Paint().apply {
            textSize = 14f; isFakeBoldText = true; color = android.graphics.Color.parseColor("#1976D2")
        }
        canvas.drawText("TOTAL: Rs.${"%.2f".format(total)}", 40f, y, totalPaint)

        document.finishPage(page)

        val fileName = "ChexoScan_Bill_${System.currentTimeMillis()}.pdf"
        var savedUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    document.writeTo(out)
                }
                savedUri = uri
            }
        } else {
            // Android 9 and below
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> document.writeTo(out) }
            savedUri = Uri.fromFile(file)
        }
        document.close()

        Toast.makeText(context, "PDF saved to Downloads: $fileName", Toast.LENGTH_LONG).show()

        // Open PDF
        savedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "PDF failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
// ─────────────────────────────────────────────────────────
//  PRINT — uses printer size from Settings
// ─────────────────────────────────────────────────────────
private fun getPrinterPageSize(printerSize: String): Pair<Int, Int> {
    return when (printerSize) {
        "58mm"  -> Pair(164, 500)
        "80mm"  -> Pair(226, 700)
        "A5"    -> Pair(420, 595)
        else    -> Pair(595, 842)
    }
}

private fun getPrintMediaSize(printerSize: String): PrintAttributes.MediaSize {
    return when (printerSize) {
        "58mm"  -> PrintAttributes.MediaSize("roll_58mm", "58mm", 580, 2830)
        "80mm"  -> PrintAttributes.MediaSize("roll_80mm", "80mm", 800, 2830)
        "A5"    -> PrintAttributes.MediaSize("iso_a5", "A5", 14800, 21000)
        else    -> PrintAttributes.MediaSize("iso_a4", "A4", 21000, 29700)
    }
}

private fun printBill(context: Context, products: List<LocalProduct>, username: String) {
    try {
        val printerSize = SettingsData.getPrinterSize(context)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "ChexoScan_Bill_${System.currentTimeMillis()}"
        val adapter = BillPrintAdapter(context, products, username, printerSize)
        val mediaSize = getPrintMediaSize(printerSize)
        val attrs = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        printManager.print(jobName, adapter, attrs)
    } catch (e: Exception) {
        Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private class BillPrintAdapter(
    private val ctx: Context,
    private val products: List<LocalProduct>,
    private val username: String,
    private val printerSize: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal,
        layoutResultCallback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        val info = PrintDocumentInfo.Builder("ChexoScan_Bill.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        layoutResultCallback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal,
        callback: WriteResultCallback
    ) {
        val document = PdfDocument()
        val page = drawBill(document, products, username, printerSize)
        document.finishPage(page)
        try {
            FileOutputStream(destination.fileDescriptor).use { out ->
                document.writeTo(out)
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            document.close()
        }
    }
}

private fun drawBill(document: PdfDocument, products: List<LocalProduct>, username: String, printerSize: String = "A4"): PdfDocument.Page {
    val (pageWidth, pageHeight) = getPrinterPageSize(printerSize)
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    val margin = 20f
    val usableWidth = pageWidth - margin * 2
    val rateX = margin + usableWidth * 0.55f
    val amountX = margin + usableWidth * 0.78f
    val lineRight = pageWidth - margin

    val isSmall = pageWidth < 300
    val titleSize = if (isSmall) 14f else 20f
    val headerSize = if (isSmall) 10f else 12f
    val bodySize = if (isSmall) 9f else 11f
    val totalSize = if (isSmall) 11f else 14f

    val titlePaint = android.graphics.Paint().apply {
        setTextSize(titleSize); isFakeBoldText = true; setColor(android.graphics.Color.parseColor("#1976D2"))
    }
    val textPaint = android.graphics.Paint().apply { setTextSize(bodySize); setColor(android.graphics.Color.DKGRAY) }
    val headerPaint = android.graphics.Paint().apply {
        setTextSize(headerSize); isFakeBoldText = true; setColor(android.graphics.Color.BLACK)
    }
    val linePaint = android.graphics.Paint().apply {
        setColor(android.graphics.Color.LTGRAY); strokeWidth = 1f
    }

    var y = 30f
    val titleText = if (isSmall) "Bill" else "ChexoScan - Bill"
    canvas.drawText(titleText, margin, y, titlePaint)
    y += titleSize + 8f
    canvas.drawText("Shop: $username", margin, y, textPaint)
    y += bodySize + 4f
    canvas.drawText("Date: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}", margin, y, textPaint)
    y += bodySize + 8f
    canvas.drawLine(margin, y, lineRight, y, linePaint)
    y += 12f

    canvas.drawText("Item", margin, y, headerPaint)
    canvas.drawText("Rate", rateX, y, headerPaint)
    canvas.drawText("Amount", amountX, y, headerPaint)
    y += 3f
    canvas.drawLine(margin, y, lineRight, y, linePaint)
    y += 12f

    var total = 0.0
    for (p in products) {
        val amount = p.customerPrice * p.quantity
        total += amount
        val maxNameLen = if (isSmall) 15 else 30
        canvas.drawText(p.name.take(maxNameLen), margin, y, textPaint)
        canvas.drawText("Rs.${p.customerPrice}", rateX, y, textPaint)
        canvas.drawText("Rs.${"%.2f".format(amount)}", amountX, y, textPaint)
        y += bodySize + 6f
    }

    y += 5f
    canvas.drawLine(margin, y, lineRight, y, linePaint)
    y += 15f
    val totalPaint = android.graphics.Paint().apply {
        setTextSize(totalSize); isFakeBoldText = true; setColor(android.graphics.Color.parseColor("#1976D2"))
    }
    canvas.drawText("TOTAL: Rs.${"%.2f".format(total)}", margin, y, totalPaint)
    return page
}
