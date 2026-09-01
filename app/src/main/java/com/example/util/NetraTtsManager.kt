package com.example.util

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Calendar
import java.util.Locale

class NetraTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var speakingFinishedCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    speakingFinishedCallback?.invoke()
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    fun setSpeakingFinishedCallback(callback: () -> Unit) {
        this.speakingFinishedCallback = callback
    }

    /**
     * Checks whether active audio output (music, media playback, or phone call) is occurring.
     */
    fun isAudioInterruptionActive(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val isCallActive = audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
            val isMediaActive = audioManager.isMusicActive
            isCallActive || isMediaActive
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if current time is within Night Mode (10:00 PM to 6:00 AM).
     */
    fun isNightModeActive(now: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 6
    }

    fun speakAlert(message: String, isCriticalSafety: Boolean = false) {
        if (!isInitialized) return

        // ABSOLUTE BAN: Security Hub / Netra must NEVER announce battery percentage,
        // charger connected/disconnected, or charging rates via voice.
        val lower = message.lowercase()
        if (lower.contains("battery percent") || lower.contains("charger connected") || 
            lower.contains("charger disconnected") || lower.contains("charging at") ||
            lower.contains("battery target") || lower.contains("charging milestone") ||
            lower.contains("battery level")) {
            return // Silently drop prohibited battery voice announcements
        }

        // Suppress during media playback or phone call
        if (isAudioInterruptionActive()) {
            return
        }

        // In Night Mode, suppress non-critical announcements
        if (isNightModeActive() && !isCriticalSafety) {
            return
        }

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NetraAlertId")
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "NetraAlertId")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

