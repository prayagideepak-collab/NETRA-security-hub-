package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class NetraTtsManager(context: Context) : TextToSpeech.OnInitListener {

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

    fun speakAlert(message: String) {
        if (isInitialized) {
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "NetraAlertId")
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, params, "NetraAlertId")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
