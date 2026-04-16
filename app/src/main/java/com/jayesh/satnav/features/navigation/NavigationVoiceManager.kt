package com.jayesh.satnav.features.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jayesh.satnav.core.utils.NavLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Manages Text-to-Speech for navigation instructions with proper timing and queuing.
 *
 * Features:
 * - Offline TTS support (uses device's offline TTS engine)
 * - Instruction queuing to avoid overlap
 * - Distance-based timing logic
 * - Lifecycle management (init/shutdown)
 * - Duplicate prevention
 */
@Singleton
class NavigationVoiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Confined to Main — all mutations happen here, matching TTS callback dispatch
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tts: TextToSpeech? = null
    @Volatile private var isInitialized = false
    @Volatile private var initializationError: String? = null

    // All queue mutations happen on Main thread only (scope above ensures this)
    private val speechQueue = mutableListOf<SpeechItem>()
    private var isSpeaking = false

    // Timing thresholds (meters)
    companion object {
        private const val TAG = "NavigationVoiceManager"
        
        // Distance thresholds for voice prompts
        const val FAR_WARNING_DISTANCE = 300.0 // meters
        const val NEAR_WARNING_DISTANCE = 50.0  // meters
        const val IMMEDIATE_DISTANCE = 10.0     // meters
        
        // Minimum time between same instruction repeats (seconds)
        private const val MIN_REPEAT_INTERVAL_MS = 5000L
    }

    data class SpeechItem(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val instructionId: String? = null, // For duplicate prevention
        val priority: Int = 0, // Higher priority interrupts lower priority
    )

    // Track last spoken instructions to prevent repeats
    private val lastSpokenInstructions = mutableMapOf<String, Long>() // instructionId -> timestamp

    fun initialize() {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_MISSING_DATA
                isInitialized = when (result) {
                    TextToSpeech.LANG_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                        NavLog.voice("TTS initialized successfully with US English")
                        true
                    }
                    TextToSpeech.LANG_MISSING_DATA -> {
                        initializationError = "TTS language data missing"
                        NavLog.voice("TTS language data missing")
                        false
                    }
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        initializationError = "TTS language not supported"
                        NavLog.voice("TTS language not supported")
                        false
                    }
                    else -> {
                        initializationError = "TTS initialization failed with code $result"
                        NavLog.voice("TTS initialization failed: $result")
                        false
                    }
                }

                if (isInitialized) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isSpeaking = true
                            NavLog.voice("TTS started: $utteranceId")
                        }

                        override fun onDone(utteranceId: String?) {
                            isSpeaking = false
                            NavLog.voice("TTS completed: $utteranceId")
                            processNextInQueue()
                        }

                        @Deprecated("Legacy TextToSpeech callback kept for platform compatibility")
                        override fun onError(utteranceId: String?) {
                            isSpeaking = false
                            NavLog.voice("TTS error: $utteranceId")
                            processNextInQueue()
                        }
                    })
                }
            } else {
                initializationError = "TTS initialization failed with status $status"
                NavLog.voice("TTS initialization failed with status $status")
                isInitialized = false
            }
        }
    }

    fun speak(text: String, instructionId: String? = null, priority: Int = 0) {
        if (!isInitialized) {
            NavLog.voice("TTS not initialized, cannot speak: $text")
            return
        }

        // Check if this instruction was recently spoken
        if (instructionId != null) {
            val lastTime = lastSpokenInstructions[instructionId]
            if (lastTime != null && System.currentTimeMillis() - lastTime < MIN_REPEAT_INTERVAL_MS) {
                NavLog.voice("Skipping recently spoken instruction: $instructionId")
                return
            }
            lastSpokenInstructions[instructionId] = System.currentTimeMillis()
        }

        val item = SpeechItem(text = text, instructionId = instructionId, priority = priority)
        
        // If higher priority or nothing speaking, interrupt and speak immediately
        if (priority > 0 && (isSpeaking || speechQueue.any { it.priority < priority })) {
            NavLog.voice("High priority speech, interrupting: $text")
            tts?.stop()
            isSpeaking = false
            speechQueue.clear()
            speakImmediately(item)
        } else {
            speechQueue.add(item)
            if (!isSpeaking) {
                processNextInQueue()
            }
        }
    }

    private fun speakImmediately(item: SpeechItem) {
        tts?.speak(item.text, TextToSpeech.QUEUE_FLUSH, null, item.id)
        isSpeaking = true
    }

    private fun processNextInQueue() {
        if (isSpeaking || speechQueue.isEmpty()) return

        val item = speechQueue.removeFirst()
        tts?.speak(item.text, TextToSpeech.QUEUE_ADD, null, item.id)
        isSpeaking = true
    }

    fun speakNavigationInstruction(
        instruction: String,
        distanceToTurn: Double,
        instructionId: String,
    ) {
        val spokenText = when {
            distanceToTurn > FAR_WARNING_DISTANCE -> {
                // Only speak far warnings for significant turns
                if (distanceToTurn > 500) {
                    val distanceKm = distanceToTurn / 1000
                    if (distanceKm >= 1.0) {
                        "In ${"%.1f".format(distanceKm)} kilometers, $instruction"
                    } else {
                        "In ${distanceToTurn.toInt()} meters, $instruction"
                    }
                } else {
                    null // Don't speak too far warnings for short distances
                }
            }
            distanceToTurn > NEAR_WARNING_DISTANCE -> {
                "In ${distanceToTurn.toInt()} meters, $instruction"
            }
            distanceToTurn > IMMEDIATE_DISTANCE -> {
                instruction
            }
            else -> {
                "Now, $instruction"
            }
        }

        spokenText?.let {
            // Higher priority for immediate instructions
            val priority = when {
                distanceToTurn <= IMMEDIATE_DISTANCE -> 2
                distanceToTurn <= NEAR_WARNING_DISTANCE -> 1
                else -> 0
            }
            speak(it, instructionId, priority)
        }
    }

    fun speakRerouting() {
        speak("Rerouting", "rerouting", priority = 2)
    }

    fun speakArrived() {
        speak("You have arrived at your destination", "arrived", priority = 1)
    }

    fun speakOffRoute() {
        speak("You are off route", "off_route", priority = 2)
    }

    fun clearQueue() {
        speechQueue.clear()
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        clearQueue()
        tts?.shutdown()
        tts = null
        isInitialized = false
        scope.cancel()
        NavLog.voice("TTS shutdown")
    }

    fun isReady(): Boolean = isInitialized

    fun getInitializationError(): String? = initializationError
}
