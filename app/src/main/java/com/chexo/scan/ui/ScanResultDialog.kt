package com.chexo.scan.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val DarkBlue = Color(0xFF1976D2)
private val ErrorRed = Color(0xFFFF5252)

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
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(editName) }
    var quantity by remember { mutableStateOf(editQuantity.toString()) }
    var customerPrice by remember { mutableStateOf(if (editCustomerPrice > 0) editCustomerPrice.toString() else "") }
    var ourPrice by remember { mutableStateOf(if (editOurPrice > 0) editOurPrice.toString() else "") }
    var error by remember { mutableStateOf("") }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color(0xFFF0F0F0),
        unfocusedContainerColor = Color(0xFFF0F0F0),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isEdit) "Edit Product" else "Scan Result",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = DarkBlue
                )
                Spacer(Modifier.height(4.dp))
                Text("Barcode: $barcode", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))

                TextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    placeholder = { Text("Product Name", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                )

                TextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() }; error = "" },
                    placeholder = { Text("Quantity", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                )

                TextField(
                    value = customerPrice,
                    onValueChange = { customerPrice = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    placeholder = { Text("Customer Price (Rate)", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                )

                TextField(
                    value = ourPrice,
                    onValueChange = { ourPrice = it.filter { c -> c.isDigit() || c == '.' }; error = "" },
                    placeholder = { Text("Our Price (Rate)", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black)
                )

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(error, color = ErrorRed, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = {
                    if (name.isBlank()) { error = "Enter product name"; return@TextButton }
                    val qty = quantity.toIntOrNull() ?: 1
                    val cp = customerPrice.toDoubleOrNull() ?: 0.0
                    val op = ourPrice.toDoubleOrNull() ?: 0.0
                    onConfirm(name.trim(), qty, cp, op)
                }) {
                    Text(
                        if (isEdit) "Update Product" else "Save Product",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBlue
                    )
                }

                if (isEdit && onDelete != null) {
                    TextButton(onClick = { onDelete() }) {
                        Text("Delete Product", fontSize = 14.sp, color = ErrorRed)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}
