package com.example.util

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoGreenVibrant
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

enum class PinStrength(val displayName: String, val color: Color) {
    WEAK("Weak", BentoRed),
    MEDIUM("Medium", BentoAmber),
    STRONG("Strong", BentoGreenVibrant)
}

object SecurityUtils {
    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = pin + salt
        val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    fun generateRecoveryKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Easily readable characters
        val random = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until 12) {
            val index = random.nextInt(chars.length)
            sb.append(chars[index])
            if (i == 3 || i == 7) {
                sb.append("-")
            }
        }
        return sb.toString()
    }
}

object PinStrengthAnalyzer {
    fun analyze(pin: String): PinStrength {
        if (pin.length < 4 || pin.length > 6) return PinStrength.WEAK
        if (!pin.all { it.isDigit() }) return PinStrength.WEAK

        // 1. Common PINs check
        val commonPins = setOf(
            "1234", "4321", "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999",
            "1212", "1313", "1414", "2020", "000000", "111111", "222222", "333333", "444444", "555555",
            "666666", "777777", "888888", "999999", "123456", "654321", "121212"
        )
        if (pin in commonPins) {
            return PinStrength.WEAK
        }

        // 2. All digits same check
        val allSame = pin.all { it == pin[0] }
        if (allSame) return PinStrength.WEAK

        // 3. Sequential digits check (e.g., 1234, 5678, 9876, 4321)
        var isAscending = true
        var isDescending = true
        for (i in 0 until pin.length - 1) {
            val diff = pin[i + 1] - pin[i]
            if (diff != 1) isAscending = false
            if (diff != -1) isDescending = false
        }
        if (isAscending || isDescending) {
            return PinStrength.WEAK
        }

        // 4. Repeated pattern check (e.g. 1212, 123123)
        if (pin.length == 4) {
            if (pin.substring(0, 2) == pin.substring(2, 4)) {
                return PinStrength.WEAK
            }
        }
        if (pin.length == 6) {
            if (pin.substring(0, 3) == pin.substring(3, 6)) {
                return PinStrength.WEAK
            }
            if (pin.substring(0, 2) == pin.substring(2, 4) && pin.substring(2, 4) == pin.substring(4, 6)) {
                return PinStrength.WEAK
            }
        }

        // 5. Digit uniqueness diversity
        val uniqueDigitsCount = pin.toSet().size
        if (uniqueDigitsCount < 3) {
            return PinStrength.WEAK
        }

        // Strong Criteria
        // Uses 6 digits (preferred), no repeated patterns, no sequences, no common combinations, good diversity (unique digits >= 4)
        if (pin.length == 6 && uniqueDigitsCount >= 4) {
            return PinStrength.STRONG
        }

        return PinStrength.MEDIUM
    }
}
