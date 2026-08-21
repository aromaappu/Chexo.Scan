package com.chexo.scan

import java.security.MessageDigest

object PasswordHasher {
    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun matches(password: String, storedHash: String): Boolean {
        return hash(password).equals(storedHash, ignoreCase = true)
    }
}
