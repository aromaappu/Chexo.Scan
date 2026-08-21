package com.chexo.scan

import android.content.Context

object AuthPrefs {
    private const val PREFS = "chexo_auth"
    private const val KEY_USERNAME = "username"
    private const val KEY_LOGGED_IN = "logged_in"

    fun saveSession(context: Context, username: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)
    }

    fun getUsername(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USERNAME, "User") ?: "User"
    }
}
