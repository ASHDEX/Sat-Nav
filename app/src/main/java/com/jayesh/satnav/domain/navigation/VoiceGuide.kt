package com.jayesh.satnav.domain.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority levels for voice announcements.
 */
enum class Priority {
    Low,      // Drop if busy
    Normal,   // Add to queue
    High      // Flush queue and speak immediately
}

/**
 * Wrapper around Android TextToSpeech for voice guidance.
 * Initializes lazily and manages queue based on priority.
 */
@Singleton
class VoiceGuide @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initializationError: String? = null
    
    private val pendingQueue = mutableListOf<QueuedUtterance>()
    private var isSpeaking = false
    
    /**
     * Speak the given text with specified priority.
     * Returns true if the utterance was queued or spoken, false if dropped.
     */
    fun speak(text: String, priority: Priority = Priority.Normal): Boolean {
        ensureInitialized()
        
        if (!isInitialized) {
            return false
        }
        
        return when (priority) {
            Priority.High -> {
                // Flush queue and speak immediately
                pendingQueue.clear()
                tts?.stop()
                speakImmediately(text)
                true
            }
            Priority.Normal -> {
                // Add to queue
                pendingQueue.add(QueuedUtterance(text))
                if (!isSpeaking) {
                    processNextInQueue()
                }
                true
            }
            Priority.Low -> {
                // Drop if busy, otherwise speak
                if (isSpeaking || pendingQueue.isNotEmpty()) {
                    false
                } else {
                    speakImmediately(text)
                    true
                }
            }
        }
    }
    
    /**
     * Shutdown the TTS engine.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        pendingQueue.clear()
    }
    
    private fun ensureInitialized() {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.getDefault())
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            initializationError = "Language not supported"
                        } else {
                            isInitialized = true
                            setupUtteranceListener()
                            processNextInQueue()
                        }
                    } else {
                        initializationError = "TTS initialization failed"
                    }
                }
            }
        }
    }
    
    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                processNextInQueue()
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                processNextInQueue()
            }
            
            @Deprecated("Deprecated in Android")
            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                processNextInQueue()
            }
        })
    }
    
    private fun speakImmediately(text: String): Boolean {
        return tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "high_priority") == TextToSpeech.SUCCESS
    }
    
    private fun processNextInQueue() {
        if (pendingQueue.isNotEmpty() && !isSpeaking) {
            val next = pendingQueue.removeAt(0)
            tts?.speak(next.text, TextToSpeech.QUEUE_ADD, null, "normal_priority")
        }
    }
    
    private data class QueuedUtterance(val text: String)
}