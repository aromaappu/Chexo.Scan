package com.chexo.scan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chexo.scan.data.AppDatabase
import com.chexo.scan.data.LocalProduct
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Text("ChexoScan", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Welcome, $username", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White, modifier = Modifier.size(24.dp))
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

// ─────────────────────────────────────────────────────────
//  SELL PAGE — Product list, PDF, Print, Generate Bill
// ─────────────────────────────────────────────────────────
@Composable
private fun SellPage(username: String, modifier: Modifier = Modifier) {
    var products by remember { mutableStateOf<List<LocalProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var generatingBill by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(username) {
        loading = true
        try { products = db.localProductDao().getAllForUser(username) } catch (_: Exception) {}
        loading = false
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
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
                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { generatePdf(context, products, username) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("PDF", fontSize = 13.sp, color = DarkBlue)
                    }
                    OutlinedButton(
                        onClick = { printBill(context, products, username) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkBlue)
                        Spacer(Modifier.width(4.dp))
                        Text("Print", fontSize = 13.sp, color = DarkBlue)
                    }
                    Button(
                        onClick = {
                            generatingBill = true
                            scope.launch {
                                try {
                                    val ids = products.map { it.id }
                                    db.localProductDao().deleteByIds(ids)
                                    products = emptyList()
                                    Toast.makeText(context, "Bill generated! Products cleared.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                generatingBill = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
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

                // Total
                val total = products.sumOf { it.customerPrice * it.quantity }
                Text(
                    "Total: Rs.${"%.2f".format(total)}  |  Items: ${products.size}",
                    fontSize = 13.sp, color = DarkBlue, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn {
                    items(products, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontSize = 14.sp, color = Color.Black)
                                Text("Barcode: ${product.barcode} | Qty: ${product.quantity}", fontSize = 11.sp, color = IconGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Rs.${product.customerPrice * product.quantity}", fontSize = 13.sp, color = DarkBlue, fontWeight = FontWeight.Bold)
                                Text("Unit: Rs.${product.customerPrice}", fontSize = 11.sp, color = IconGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  ADD PAGE — Scanner + list of added products
// ─────────────────────────────────────────────────────────
@Composable
private fun AddPage(username: String, modifier: Modifier = Modifier) {
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<LocalProduct>>(emptyList()) }
    var editingProduct by remember { mutableStateOf<LocalProduct?>(null) }
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

    Column(modifier = modifier.fillMaxWidth()) {
        // Scanner area
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add Product", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
            Spacer(Modifier.height(4.dp))
            Text("Saved under account: $username", fontSize = 13.sp, color = IconGray)
            Spacer(Modifier.height(10.dp))
            BarcodeScannerView(onBarcodeScanned = { scannedBarcode = it })
        }

        // Product list below scanner
        if (products.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Added Products (${products.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkBlue,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(products, key = { it.id }) { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable {
                                editingProduct = product
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Medium)
                            Text("Barcode: ${product.barcode}", fontSize = 11.sp, color = IconGray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Qty: ${product.quantity}", fontSize = 12.sp, color = DarkBlue, fontWeight = FontWeight.Bold)
                            Text("C: Rs.${product.customerPrice} | O: Rs.${product.ourPrice}", fontSize = 10.sp, color = IconGray)
                        }
                    }
                }
            }
        }
    }

    // Scan dialog (new product)
    if (scannedBarcode != null && editingProduct == null) {
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
//  ANALYSIS PAGE
// ─────────────────────────────────────────────────────────
@Composable
private fun AnalysisPage(username: String, modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }
    var totalValue by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    LaunchedEffect(username) {
        loading = true
        try {
            val prods = db.localProductDao().getAllForUser(username)
            count = prods.size
            totalValue = prods.sumOf { it.customerPrice * it.quantity }
        } catch (_: Exception) {}
        loading = false
    }

    Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkBlue)
            Spacer(Modifier.height(8.dp))
            Text("Account: $username", fontSize = 13.sp, color = IconGray)
            Spacer(Modifier.height(16.dp))
            if (loading) {
                CircularProgressIndicator(color = DarkBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            } else {
                Text("$count products in your account", fontSize = 16.sp, color = Color.Black)
                Spacer(Modifier.height(4.dp))
                Text("Total Value: Rs.${"%.2f".format(totalValue)}", fontSize = 14.sp, color = DarkBlue, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  FOOTER ICON
// ─────────────────────────────────────────────────────────
@Composable
private fun FooterIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .background(if (selected) DarkBlue.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) DarkBlue else IconGray, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) DarkBlue else IconGray)
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
        canvas.drawText("Account: $username", 40f, y, textPaint)
        y += 15f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Date: $dateStr", 40f, y, textPaint)
        y += 25f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 20f

        // Headers
        canvas.drawText("Item", 40f, y, headerPaint)
        canvas.drawText("Qty", 280f, y, headerPaint)
        canvas.drawText("Rate", 340f, y, headerPaint)
        canvas.drawText("Amount", 450f, y, headerPaint)
        y += 5f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 18f

        var total = 0.0
        for (p in products) {
            val amount = p.customerPrice * p.quantity
            total += amount
            canvas.drawText(p.name.take(25), 40f, y, textPaint)
            canvas.drawText("${p.quantity}", 285f, y, textPaint)
            canvas.drawText("Rs.${p.customerPrice}", 340f, y, textPaint)
            canvas.drawText("Rs.${"%.2f".format(amount)}", 440f, y, textPaint)
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

        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, "ChexoScan_Bill_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()

        Toast.makeText(context, "PDF saved: ${file.name}", Toast.LENGTH_LONG).show()

        // Open PDF
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(file), "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "PDF failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────
//  PRINT
// ─────────────────────────────────────────────────────────
private fun printBill(context: Context, products: List<LocalProduct>, username: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "ChexoScan_Bill_${System.currentTimeMillis()}"

        printManager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                layoutResultCallback: PrintDocumentAdapter.LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                val info = PrintDocumentInfo.Builder("ChexoScan_Bill.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                layoutResultCallback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: PrintDocumentAdapter.WriteResultCallback
            ) {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = android.graphics.Paint().apply {
                    textSize = 20f; isFakeBoldText = true; color = android.graphics.Color.parseColor("#1976D2")
                }
                val textPaint = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.DKGRAY }
                val headerPaint = android.graphics.Paint().apply {
                    textSize = 12f; isFakeBoldText = true; color = android.graphics.Color.BLACK
                }
                val linePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.LTGRAY; strokeWidth = 1f
                }

                var y = 50f
                canvas.drawText("ChexoScan - Bill", 40f, y, titlePaint)
                y += 25f
                canvas.drawText("Account: $username", 40f, y, textPaint)
                y += 15f
                canvas.drawText("Date: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}", 40f, y, textPaint)
                y += 25f
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 20f

                canvas.drawText("Item", 40f, y, headerPaint)
                canvas.drawText("Qty", 280f, y, headerPaint)
                canvas.drawText("Rate", 340f, y, headerPaint)
                canvas.drawText("Amount", 450f, y, headerPaint)
                y += 5f
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 18f

                var total = 0.0
                for (p in products) {
                    val amount = p.customerPrice * p.quantity
                    total += amount
                    canvas.drawText(p.name.take(25), 40f, y, textPaint)
                    canvas.drawText("${p.quantity}", 285f, y, textPaint)
                    canvas.drawText("Rs.${p.customerPrice}", 340f, y, textPaint)
                    canvas.drawText("Rs.${"%.2f".format(amount)}", 440f, y, textPaint)
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

                FileOutputStream(destination.fileDescriptor).use { out ->
                    document.writeTo(out)
                }
                document.close()
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }
        }, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        Toast.makeText(context, "Print failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
