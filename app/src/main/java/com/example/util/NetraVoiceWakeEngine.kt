package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.ui.MainViewModel
import com.example.ui.assistant.NetraAssistantBrain
import java.util.Locale

class NetraVoiceWakeEngine(
    private val context: Context,
    private val viewModel: MainViewModel,
    private val ttsManager: NetraTtsManager
) {
    private val tag = "NetraVoiceWakeEngine"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isStarted = false
    private val handler = Handler(Looper.getMainLooper())

    enum class EngineState {
        IDLE,
        WAKE_WORD,
        COMMAND
    }

    private var currentState = EngineState.IDLE

    var onStateChanged: ((EngineState) -> Unit)? = null
    var onCommandHeard: ((String) -> Unit)? = null
    var onResponseSpoken: ((String) -> Unit)? = null

    init {
        ttsManager.setSpeakingFinishedCallback {
            handler.post {
                if (isStarted) {
                    if (currentState == EngineState.COMMAND) {
                        // After speaking "How can I help you?", we transition and listen for command.
                        // We set a brief delay to make sure the audio channel/mic has fully released
                        handler.postDelayed({
                            startCommandListening()
                        }, 200)
                    } else {
                        // After speaking the answer, go back to wake word state
                        handler.postDelayed({
                            startWakeWordListening()
                        }, 200)
                    }
                }
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error"
            }
            Log.e(tag, "SpeechRecognizer error: $errorMsg ($error)")

            // Restart listening if we are still active
            if (isStarted) {
                handler.postDelayed({
                    resumeListening()
                }, 1000)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0].lowercase(Locale.ROOT).trim()
                Log.d(tag, "Heard: $text")
                handleHeardText(text)
            } else {
                if (isStarted) {
                    resumeListening()
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        if (isStarted) return
        isStarted = true
        currentState = EngineState.WAKE_WORD
        onStateChanged?.invoke(currentState)
        
        handler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(recognitionListener)
                    }
                    startWakeWordListening()
                    Log.i(tag, "Voice Wake Engine Started successfully.")
                } else {
                    Log.w(tag, "Speech Recognition is not available on this device.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error starting voice wake engine", e)
            }
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        currentState = EngineState.IDLE
        onStateChanged?.invoke(currentState)
        
        handler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
                Log.i(tag, "Voice Wake Engine Stopped successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Error stopping voice wake engine", e)
            }
        }
    }

    private fun startWakeWordListening() {
        if (!isStarted) return
        currentState = EngineState.WAKE_WORD
        onStateChanged?.invoke(currentState)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
        }
    }

    private fun startCommandListening() {
        if (!isStarted) return
        currentState = EngineState.COMMAND
        onStateChanged?.invoke(currentState)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start command listening", e)
        }
    }

    private fun resumeListening() {
        if (!isStarted) return
        if (currentState == EngineState.COMMAND) {
            startCommandListening()
        } else {
            startWakeWordListening()
        }
    }

    private fun handleHeardText(text: String) {
        if (currentState == EngineState.WAKE_WORD) {
            // Check for wake word "Hi Netra" or "Netra" or variants
            if (text.contains("hi netra") || text.contains("netra") || text.contains("hello netra")) {
                Log.i(tag, "Wake phrase detected!")
                speechRecognizer?.stopListening()
                
                currentState = EngineState.COMMAND
                onStateChanged?.invoke(currentState)
                
                ttsManager.speakAlert("How can I help you?")
            } else {
                startWakeWordListening()
            }
        } else if (currentState == EngineState.COMMAND) {
            Log.i(tag, "Command detected: $text")
            onCommandHeard?.invoke(text)
            
            speechRecognizer?.stopListening()
            
            // Process query
            val reply = NetraAssistantBrain.processQuery(text, viewModel)
            val ttsFriendlyReply = cleanTtsMessage(reply)
            
            onResponseSpoken?.invoke(reply)
            ttsManager.speakAlert(ttsFriendlyReply)
        }
    }

    private fun cleanTtsMessage(message: String): String {
        return message
            .replace("**", "")
            .replace("*", "")
            .replace("•", "")
            .replace("✅", "")
            .replace("⚠️", "Warning:")
            .replace("🚨", "Emergency Alert!")
            .replace("🔴", "Hazard!")
            .replace("🟢", "Normal.")
            .replace("🔋", "")
            .replace("🧲", "")
            .replace("📱", "")
            .replace("🚗", "")
            .replace("🛡️", "")
            .replace("📊", "")
            .trim()
    }
}
