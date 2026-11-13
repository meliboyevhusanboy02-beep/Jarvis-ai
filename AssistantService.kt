
package uz.jarvis.ai

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener

import java.util.Locale
import java.io.IOException

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import org.json.JSONArray
import org.json.JSONObject

class AssistantService : Service(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var language = "uz-UZ"
    private var awaitingCommand = false
    private var restarting = false
    private var stopping = false
    private var ttsReady = false

    private val prefs by lazy {
        getSharedPreferences("jarvis_memory", MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()

        stopping = false

        tts = TextToSpeech(this, this)

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    scheduleRestart(500)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    scheduleRestart(700)
                }
            }
        )

        createForegroundNotification()
    }

    private fun createForegroundNotification() {

        val channelId = "jarvis_assistant"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                channelId,
                "JARVIS voice assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AssistantService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("JARVIS AI faol")
            .setContentText("Ovozli yordamchi ishlayapti")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(
                android.R.drawable.ic_media_pause,
                "To‘xtatish",
                stop
            )
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        stopping = false

        language = intent?.getStringExtra("language")
            ?: prefs.getString("language", "uz-UZ")
            ?: "uz-UZ"

        startRecognitionLoop()

        return START_STICKY
    }

    private fun startRecognitionLoop() {

        if (restarting || stopping) return

        restarting = true

        handler.post {

            restarting = false

            if (stopping) return@post

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                speak("Nutqni aniqlash xizmati mavjud emas")
                return@post
            }

            try {

                recognizer?.destroy()

                recognizer = SpeechRecognizer.createSpeechRecognizer(this)

                recognizer?.setRecognitionListener(
                    object : RecognitionListener {

                        override fun onReadyForSpeech(params: Bundle?) {}

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            scheduleRestart(1000)
                        }

                        override fun onResults(results: Bundle?) {

                            if (stopping) return

                            val spoken = results
                                ?.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                                )
                                ?.firstOrNull()
                                .orEmpty()

                            val normalized = spoken.lowercase(Locale.ROOT)

                            if (awaitingCommand) {

                                awaitingCommand = false

                                if (spoken.isNotBlank()) {
                                    handleVoiceCommand(spoken)
                                } else {
                                    scheduleRestart(500)
                                }

                                return
                            }

                            val wakeWords = listOf(
                                "jarvis",
                                "джарвис",
                                "жарвис"
                            )

                            val wake = wakeWords.firstOrNull {
                                normalized.contains(it)
                            }

                            if (wake != null) {

                                val command = normalized
                                    .substringAfter(wake)
                                    .trim()

                                if (command.isNotBlank()) {
                                    handleVoiceCommand(command)
                                } else {
                                    awaitingCommand = true
                                    speak("Eshitaman, buyruq bering")
                                }

                            } else {
                                scheduleRestart(700)
                            }
                        }

                        override fun onPartialResults(
                            partialResults: Bundle?
                        ) {}

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle?
                        ) {}
                    }
                )

                val recognitionIntent = Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        language
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        false
                    )
                }

                recognizer?.startListening(recognitionIntent)

            } catch (_: Exception) {
                scheduleRestart(1500)
            }
        }
    }

    private fun handleVoiceCommand(command: String) {

        val c = command.lowercase(Locale.ROOT)

        if (
            c.contains("qabul qil") ||
            c.contains("ответь") ||
            c.contains("answer call")
        ) {

            JarvisInCallService.answerActiveCall()

            speak("Qo‘ng‘iroqni qabul qilish buyrug‘i yuborildi")

            return
        }

        if (
            c.contains("rad et") ||
            c.contains("отклони") ||
            c.contains("reject call")
        ) {

            JarvisInCallService.rejectActiveCall()

            speak("Qo‘ng‘iroqni rad etish buyrug‘i yuborildi")

            return
        }

        askAi(command)
    }

    private fun askAi(message: String) {

        val history = try {
            JSONArray(prefs.getString("history", "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        history.put(
            JSONObject()
                .put("role", "user")
                .put("content", message)
        )

        val payload = JSONObject()
            .put("message", message)
            .put("language", language)
            .put("history", history)

        val request = Request.Builder()
            .url(BuildConfig.BASE_URL.trimEnd('/') + "/chat")
            .post(
                payload.toString().toRequestBody(
                    "application/json".toMediaType()
                )
            )
            .build()

        client.newCall(request).enqueue(
            object : okhttp3.Callback {

                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException
                ) {

                    handler.post {
                        if (!stopping) {
                            speak(
                                "Internet yoki JARVIS serveriga ulanishda muammo bor"
                            )
                        }
                    }
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response
                ) {

                    val reply = response.use { res ->

                        val body = res.body?.string().orEmpty()

                        if (!res.isSuccessful) {
                            "Serverda xatolik yuz berdi"
                        } else {
                            try {
                                JSONObject(body).optString(
                                    "reply",
                                    "Javob olinmadi"
                                )
                            } catch (_: Exception) {
                                "Server javobini o‘qib bo‘lmadi"
                            }
                        }
                    }

                    handler.post {

                        if (stopping) return@post

                        history.put(
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", reply)
                        )

                        val trimmed = JSONArray()
                        val from = maxOf(0, history.length() - 16)

                        for (i in from until history.length()) {
                            trimmed.put(history.getJSONObject(i))
                        }

                        prefs.edit()
                            .putString("history", trimmed.toString())
                            .apply()

                        speak(reply)
                    }
                }
            }
        )
    }

    private fun scheduleRestart(delay: Long) {

        if (restarting || stopping) return

        restarting = true

        handler.postDelayed({

            restarting = false

            if (!stopping) {
                startRecognitionLoop()
            }

        }, delay)
    }

    private fun speak(text: String) {

        if (stopping) return

        if (ttsReady && tts != null) {

            val result = tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_voice_reply"
            )

            if (result == TextToSpeech.ERROR) {
                scheduleRestart(1000)
            }

        } else {
            scheduleRestart(1000)
        }
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val locale = when (language) {
                "ru-RU" -> Locale("ru", "RU")
                "en-US" -> Locale.US
                else -> Locale("uz", "UZ")
            }

            val result = tts?.setLanguage(locale)

            ttsReady = result != null &&
                result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            if (ttsReady && !stopping) {
                startRecognitionLoop()
            }
        }
    }

    override fun onDestroy() {

        stopping = true

        handler.removeCallbacksAndMessages(null)

        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        client.dispatcher.cancelAll()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
