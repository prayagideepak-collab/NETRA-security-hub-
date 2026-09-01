package com.example

import com.example.data.service.Pbkdf2PinStorageService
import com.example.ui.PinChangeViewModel
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PinSecurityTest {

    private val pinStorageService = Pbkdf2PinStorageService()

    @Test
    fun testPbkdf2HashingAndVerification() {
        val pin = "937654"
        val salt = pinStorageService.generateSalt()
        
        // Ensure salt is generated and not empty
        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())

        val hash = pinStorageService.hashPin(pin, salt)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        // Verification of correct PIN
        val isValid = pinStorageService.verifyPin(pin, hash, salt)
        assertTrue("PBKDF2 verification should succeed for correct PIN", isValid)

        // Verification of incorrect PIN
        val isInvalid = pinStorageService.verifyPin("111111", hash, salt)
        assertFalse("PBKDF2 verification should fail for incorrect PIN", isInvalid)
    }

    @Test
    fun testPinStrengthAnalyzer() {
        // Weak PINs (repeating or sequential)
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("1111"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("1234"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("000000"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("121212"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("9988")) // Too repeating/low diversity

        // Medium PINs (some duplicate characters or simple patterns)
        assertEquals(PinStrength.MEDIUM, PinStrengthAnalyzer.analyze("4821")) // Short but distinct
        assertEquals(PinStrength.MEDIUM, PinStrengthAnalyzer.analyze("9879")) // Length 4, 3 unique digits

        // Strong PINs (unique digits, no sequences, 6 digits)
        assertEquals(PinStrength.STRONG, PinStrengthAnalyzer.analyze("937654"))
        assertEquals(PinStrength.STRONG, PinStrengthAnalyzer.analyze("825471"))
    }
}
