package com.chexo.scan

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String? = null,
    val username: String? = null,
    val barcode: String? = null,
    val name: String? = null,
    val price: Double? = null
)
