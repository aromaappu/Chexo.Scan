package com.chexo.scan

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ProductRepository {

    suspend fun listForUser(username: String): List<Product> {
        if (username.isBlank()) return emptyList()
        return SupabaseClient.client.from("products")
            .select(columns = Columns.list("id", "username", "barcode", "name", "price")) {
                filter { eq("username", username) }
            }
            .decodeList()
    }

    suspend fun addForUser(
        username: String,
        barcode: String,
        name: String,
        price: Double
    ) {
        require(username.isNotBlank()) { "Not logged in" }
        SupabaseClient.client.from("products").insert(
            buildJsonObject {
                put("username", username)
                put("barcode", barcode)
                put("name", name)
                put("price", price)
            }
        )
    }
}
