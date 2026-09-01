package com.example.data.service

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface PinStorageService {
    fun generateSalt(): String
    fun hashPin(pin: String, salt: String): String
    fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean
}

class Pbkdf2PinStorageService : PinStorageService {
    companion object {
        private const val ITERATIONS = 12000 // Slow down brute force significantly
        private const val KEY_LENGTH = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    override fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(32) // Strong 256-bit salt
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    override fun hashPin(pin: String, salt: String): String {
        return try {
            val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
            val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback to SHA-256 with strong salt in case of platform crypto issues
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val input = pin + salt
            val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        }
    }

    override fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean {
        val enteredHash = hashPin(enteredPin, storedSalt)
        // Constant-time comparison to prevent timing attacks
        return constantTimeAreEqual(enteredHash, storedHash)
    }

    private fun constantTimeAreEqual(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        if (aBytes.size != bBytes.size) return false
        var result = 0
        for (i in aBytes.indices) {
            result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
        }
        return result == 0
    }
}
