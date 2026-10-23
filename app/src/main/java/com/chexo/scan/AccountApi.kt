package com.chexo.scan

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * All cloud account operations go through a Google-ID-token-verified Supabase
 * Edge Function. Never expose the users table or a service-role key to the APK.
 * Shop inventory and sales NEVER leave the local Room database.
 */
object AccountApi {
    private const val ENDPOINT =
        "https://YOUR_PROJECT_REF.supabase.co/functions/v1/chexoscan-account"

    data class Account(val username: String, val email: String)

    private suspend fun request(action: String, idToken: String, shopName: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            require(idToken.isNotBlank()) { "Google sign-in is required." }
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            try {
                val payload = JSONObject().put("action", action)
                if (shopName != null) payload.put("username", shopName)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val body = (if (connection.responseCode in 200..299)
                    connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                val result = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException(result.optString("error", "Account service error (${connection.responseCode})."))
                }
                result
            } finally {
                connection.disconnect()
            }
        }

    suspend fun lookup(idToken: String): Account? {
        val response = request("lookup", idToken)
        if (!response.optBoolean("found")) return null
        return Account(response.getString("username"), response.getString("email"))
    }

    suspend fun register(idToken: String, username: String): Account {
        val response = request("register", idToken, username)
        return Account(response.getString("username"), response.getString("email"))
    }

    suspend fun delete(idToken: String) {
        request("delete", idToken)
    }

    suspend fun refreshedGoogleIdToken(context: Context): String = withContext(Dispatchers.IO) {
        require(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            "Set GOOGLE_WEB_CLIENT_ID in local.properties first."
        }
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val client = GoogleSignIn.getClient(context, options)
        val signedIn = try {
            Tasks.await(client.silentSignIn(), 20, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
        signedIn?.idToken ?: throw IllegalStateException(
            "Please log in with Google before deleting your online account."
        )
    }
}
