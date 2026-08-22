package com.chexo.scan

import android.content.Context
import org.json.JSONObject
import com.chexo.scan.data.AppDatabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

import androidx.compose.runtime.mutableStateOf

object SettingsData {
    private const val PREFS = "chexo_settings"
    var themeState = mutableStateOf("System")
    private var themeInitialized = false

    // General
    fun getPrinterSize(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("printer_size", "A4") ?: "A4"
    }
    fun setPrinterSize(context: Context, size: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("printer_size", size).apply()
    }

    fun getLowStockLimit(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("low_stock_limit", 5)
    }
    fun setLowStockLimit(context: Context, limit: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("low_stock_limit", limit).apply()
    }

    fun getTheme(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("theme", "System") ?: "System"
        if (!themeInitialized) {
            themeState.value = saved
            themeInitialized = true
        }
        return saved
    }
    fun setTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("theme", theme).apply()
        themeState.value = theme
        // Force Theme.kt to recompose
        com.chexo.scan.ui.theme.themeChangeCounter.value++
    }

    // Stay logged in
    fun getStayLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("stay_logged_in", true)
    }
    fun setStayLoggedIn(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("stay_logged_in", value).apply()
    }

    // Change password via Supabase
    suspend fun changePasswordSupabase(context: Context, username: String, currentPassword: String, newPassword: String): String? {
        return try {
            val all = SupabaseClient.client.from("users")
                .select(columns = Columns.list("username", "password"))
                .decodeList<com.chexo.scan.ui.UserProfile>()
            val profile = all.find { it.username?.equals(username, ignoreCase = true) == true }
            if (profile == null) return "Account not found"
            if (profile.password.isNullOrBlank()) return "Account incomplete"
            if (!PasswordHasher.matches(currentPassword, profile.password)) return "Current password is incorrect"

            val newHash = PasswordHasher.hash(newPassword)
            SupabaseClient.client.from("users").update(
                com.chexo.scan.ui.PasswordUpdate(password = newHash)
            ) {
                filter { eq("username", username) }
            }
            null // success
        } catch (e: Exception) {
            "Failed: ${e.message?.take(100)}"
        }
    }

    // Backup
    suspend fun createBackup(context: Context, username: String): JSONObject {
        val db = AppDatabase.getInstance(context)
        val products = db.localProductDao().getAllForUser(username)
        val sales = db.saleDao().getAllForUser(username)

        val root = JSONObject()
        root.put("username", username)
        root.put("backupVersion", 1)
        root.put("createdAt", System.currentTimeMillis())

        val productsArray = org.json.JSONArray()
        for (p in products) {
            val obj = JSONObject()
            obj.put("barcode", p.barcode)
            obj.put("name", p.name)
            obj.put("quantity", p.quantity)
            obj.put("customerPrice", p.customerPrice)
            obj.put("ourPrice", p.ourPrice)
            obj.put("timestamp", p.timestamp)
            productsArray.put(obj)
        }
        root.put("products", productsArray)

        val salesArray = org.json.JSONArray()
        for (s in sales) {
            val obj = JSONObject()
            obj.put("barcode", s.barcode)
            obj.put("productName", s.productName)
            obj.put("quantitySold", s.quantitySold)
            obj.put("unitPrice", s.unitPrice)
            obj.put("ourPrice", s.ourPrice)
            obj.put("totalAmount", s.totalAmount)
            obj.put("soldAt", s.soldAt)
            salesArray.put(obj)
        }
        root.put("sales", salesArray)

        return root
    }

    suspend fun restoreBackup(context: Context, json: String, username: String): String {
        val root = JSONObject(json)
        val backupUser = root.optString("username", "")

        if (backupUser != username) {
            return "This backup belongs to '$backupUser'. Cannot restore to '$username'."
        }

        val db = AppDatabase.getInstance(context)
        val productsArray = root.getJSONArray("products")
        val salesArray = root.getJSONArray("sales")

        // Clear existing data for this user
        val existingProducts = db.localProductDao().getAllForUser(username)
        for (p in existingProducts) {
            db.localProductDao().deleteById(p.id)
        }

        // Restore products
        for (i in 0 until productsArray.length()) {
            val obj = productsArray.getJSONObject(i)
            db.localProductDao().insert(
                com.chexo.scan.data.LocalProduct(
                    username = username,
                    barcode = obj.getString("barcode"),
                    name = obj.getString("name"),
                    quantity = obj.getInt("quantity"),
                    customerPrice = obj.getDouble("customerPrice"),
                    ourPrice = obj.getDouble("ourPrice"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }

        // Restore sales
        val sales = mutableListOf<com.chexo.scan.data.Sale>()
        for (i in 0 until salesArray.length()) {
            val obj = salesArray.getJSONObject(i)
            sales.add(
                com.chexo.scan.data.Sale(
                    username = username,
                    barcode = obj.getString("barcode"),
                    productName = obj.getString("productName"),
                    quantitySold = obj.getInt("quantitySold"),
                    unitPrice = obj.getDouble("unitPrice"),
                    ourPrice = obj.getDouble("ourPrice"),
                    totalAmount = obj.getDouble("totalAmount"),
                    soldAt = obj.getLong("soldAt")
                )
            )
        }
        if (sales.isNotEmpty()) {
            db.saleDao().insertAll(sales)
        }

        return "Restored ${productsArray.length()} products and ${salesArray.length()} sales."
    }

    suspend fun clearAllData(context: Context, username: String) {
        val db = AppDatabase.getInstance(context)
        val products = db.localProductDao().getAllForUser(username)
        for (p in products) {
            db.localProductDao().deleteById(p.id)
        }
        val sales = db.saleDao().getAllForUser(username)
        for (s in sales) {
            db.saleDao().deleteById(s.id)
        }
    }

    suspend fun deleteAccount(context: Context, username: String) {
        val db = AppDatabase.getInstance(context)
        val products = db.localProductDao().getAllForUser(username)
        for (p in products) {
            db.localProductDao().deleteById(p.id)
        }
        val sales = db.saleDao().getAllForUser(username)
        for (s in sales) {
            db.saleDao().deleteById(s.id)
        }
        AuthPrefs.removeAccount(context, username)
        AuthPrefs.clearSession(context)
        // Clear settings
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("chexo_auth", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
