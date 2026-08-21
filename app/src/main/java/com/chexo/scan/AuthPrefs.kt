package com.chexo.scan

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

@Serializable
data class SavedAccount(
    val username: String,
    val email: String
)

object AuthPrefs {
    private const val PREFS = "chexo_auth"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_CURRENT_USERNAME = "current_username"
    private const val KEY_ACCOUNTS = "saved_accounts"

    fun saveSession(context: Context, username: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_CURRENT_USERNAME, username)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_CURRENT_USERNAME)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)
    }

    fun getCurrentUsername(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_USERNAME, "User") ?: "User"
    }

    fun saveAccount(context: Context, username: String, email: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val accounts = getAccounts(context).toMutableList()
        if (accounts.none { it.username.equals(username, ignoreCase = true) }) {
            accounts.add(SavedAccount(username, email))
            prefs.edit().putString(KEY_ACCOUNTS, Json.encodeToString(accounts)).apply()
        }
    }

    fun getAccounts(context: Context): List<SavedAccount> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNTS, "[]") ?: "[]"
        return try { Json.decodeFromString(json) } catch (_: Exception) { emptyList() }
    }

    fun removeAccount(context: Context, username: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val accounts = getAccounts(context).toMutableList()
        accounts.removeAll { it.username.equals(username, ignoreCase = true) }
        prefs.edit().putString(KEY_ACCOUNTS, Json.encodeToString(accounts)).apply()
    }
}
