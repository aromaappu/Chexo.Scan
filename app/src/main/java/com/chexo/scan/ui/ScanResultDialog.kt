package com.chexo.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chexo.scan.ui.theme.LocalAppColors

@Composable
fun ScanResultDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, quantity: Int, customerPrice: Double, ourPrice: Double) -> Unit,
    editName: String = "",
    editQuantity: Int = 1,
    editCustomerPrice: Double = 0.0,
    editOurPrice: Double = 0.0,
    isEdit: Boolean = false,
    isAlreadyAdded: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onAddQuantity: ((Int) -> Unit)? = null
) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(editName) }
    var quantity by remember { mutableStateOf(editQuantity.toString()) }
    var customerPrice by remember { mutableStateOf(if (editCustomerPrice > 0) editCustomerPrice.toString() else "") }
    var ourPrice by remember { mutableStateOf(if (editOurPrice > 0) editOurPrice.toString() else "") }
    var error by remember { mutableStateOf("") }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = colors.background,
        unfocusedContainerColor = colors.background,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header area with product name
                if (isAlreadyAdded) {
                    Text(
                        "Already Added!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFFF9800)
                    )
                    Spacer(Modifier.height(2.dp))
                } else if (isEdit) {
                    Text(
                        "Edit Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.primary
                    )
                    Spacer(Modifier.height(2.dp))
                } else {
                    Text(
                        "New Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Product name as header
                Text(
                    name.ifBlank { "Unnamed" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text("Barcode: $barcode", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))

                // "Already Added" quick action
                if (isAlreadyAdded && onAddQuantity != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("This product is already in your list", fontSize = 12.sp, color = Color(0xFFE65100))
                            Text("Current Qty: $editQuantity", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                        TextButton(onClick = { onAddQuantity(1) }) {
                            Text("+1", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                        TextButton(onClick = { onAddQuantity(5) }) {
                            Text("+5", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("— or edit details below —", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                }

                // Form fields
                if (name.isBlank() && !isAlreadyAdded) {
                    // Empty name for new product - show name field first
                }

                TextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    placeholder = { Text("Product Name", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                )

                TextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() }; error = "" },
                    placeholder = { Text("Quantity", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                )

                TextField(
                    value = customerPrice,
                    onValueChange = { customerPrice = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    placeholder = { Text("Customer Price (Rate)", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                )

                TextField(
                    value = ourPrice,
                    onValueChange = { ourPrice = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    placeholder = { Text("Our Price (Rate)", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 13.sp, color = colors.onBackground)
                )

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = colors.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = {
                    if (name.isBlank()) { error = "Enter product name"; return@TextButton }
                    val qty = quantity.toIntOrNull()
                    val cp = customerPrice.toDoubleOrNull()
                    val op = ourPrice.toDoubleOrNull()
                    if (qty == null || qty < 0 || cp == null || !cp.isFinite() || cp < 0 ||
                        op == null || !op.isFinite() || op < 0) {
                        error = "Enter valid quantity and non-negative prices."
                        return@TextButton
                    }
                    onConfirm(name.trim(), qty, cp, op)
                }) {
                    Text(
                        if (isEdit || isAlreadyAdded) "Update Product" else "Save Product",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }

                if (isEdit && onDelete != null) {
                    TextButton(onClick = { onDelete() }) {
                        Text("Delete Product", fontSize = 14.sp, color = colors.error)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
