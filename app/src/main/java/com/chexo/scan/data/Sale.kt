package com.chexo.scan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val barcode: String,
    val productName: String,
    val quantitySold: Int,
    val unitPrice: Double,
    val ourPrice: Double,
    val totalAmount: Double,
    val soldAt: Long = System.currentTimeMillis()
)
