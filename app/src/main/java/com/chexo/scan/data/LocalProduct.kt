package com.chexo.scan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_products")
data class LocalProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val barcode: String,
    val name: String,
    val quantity: Int,
    val customerPrice: Double,
    val ourPrice: Double,
    val timestamp: Long = System.currentTimeMillis()
)
