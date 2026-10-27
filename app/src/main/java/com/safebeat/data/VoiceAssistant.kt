package com.safebeat.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 음성 질문(TTS)과 음성 응답 인식(STT).
 * 지금은 안드로이드 기본 API를 쓰고, Pleos TextToSpeech / SpeechToText SDK로 교체할 자리다.
 * 메인 스레드에서 호출해야 한다.
 */
class VoiceAssistant(private val context: Context, private val onDriverOk: () -> Unit) {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    // "네"·"응" 같은 짧은 말은 "응급" 등과 헷갈려서 뺐다
    private val okWords = listOf("괜찮", "살아", "멀쩡", "오케이", "ok")

    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "safebeat")
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "음성 인식 불가 → 화면 버튼으로만 응답")
            return
        }
        listening = true
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    fun stopListening() {
        listening = false
        recognizer?.cancel()
    }

    fun release() {
        tts?.shutdown()
        recognizer?.destroy()
    }

    private fun check(results: Bundle?) {
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (texts.any { t -> okWords.any { t.contains(it, ignoreCase = true) } }) {
            listening = false
            onDriverOk()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            check(results)
            if (listening) startListening() // 15초 동안 계속 듣는다
        }
        override fun onPartialResults(partialResults: Bundle?) = check(partialResults)
        override fun onError(error: Int) {
            if (listening) startListening()
        }
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        private const val TAG = "SafeBeat"
    }
}
