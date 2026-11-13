package uz.jarvis.ai

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var tts: TextToSpeech
    private var language = "uz-UZ"
    private val client = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        status.text = if (result[Manifest.permission.RECORD_AUDIO] == true)
            "Mikrofon ruxsati berildi" else "Mikrofon ruxsati kerak"
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val rm = getSystemService(RoleManager::class.java)
        status.text = if (Build.VERSION.SDK_INT >= 29 && rm.isRoleHeld(RoleManager.ROLE_DIALER))
            "JARVIS standart Telefon ilovasi bo‘ldi" else "Telefon ilovasi roli berilmadi"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(34, 38, 34, 28)
        }
        val title = TextView(this).apply { text = "JARVIS AI"; textSize = 30f }
        status = TextView(this).apply { text = "Tayyor"; textSize = 15f }
        answer = TextView(this).apply {
            text = "Salom. Men JARVIS. Ovozli suhbatni boshlash uchun tugmani bosing."
            textSize = 18f
        }
        val languages = Spinner(this)
        languages.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("O‘zbekcha", "Русский", "English")
        )
        languages.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                language = listOf("uz-UZ", "ru-RU", "en-US")[position]
                prefs.edit().putString("language", language).apply()
                setTtsLanguage()
            }
        }
        val talk = Button(this).apply {
            text = "🎙 JARVIS bilan gaplashish"
            setOnClickListener { startListeningOnce() }
        }
        val background = Button(this).apply {
            text = "Fon rejimini yoqish"
            setOnClickListener { startAssistantService() }
        }
        val dialer = Button(this).apply {
            text = "JARVIS'ni standart Telefon qilish"
            setOnClickListener { requestDialerRole() }
        }
        val settings = Button(this).apply {
            text = "Ruxsatlar sozlamalari"
            setOnClickListener { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName"))) }
        }
        listOf(title, status, languages, talk, background, dialer, settings, answer).forEach { layout.addView(it) }
        setContentView(ScrollView(this).apply { addView(layout) })

        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                dialerRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else status.text = "Bu telefonda standart telefon roli mavjud emas"
        } else {
            status.text = "Android versiyasida standart telefon rolini alohida tekshirish kerak"
        }
    }

    private fun startAssistantService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); return
        }
        val i = Intent(this, AssistantService::class.java).putExtra("language", language)
        ContextCompat.startForegroundService(this, i)
        status.text = "Fon xizmati yoqildi. Doimiy uyg‘otuvchi so‘z hozircha tajriba rejimida emas."
    }

    private fun startListeningOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Nutqni aniqlash xizmati topilmadi"; return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        status.text = "Tinglayapman…"
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Gapiring…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "AI javob tayyorlamoqda…" }
            override fun onError(error: Int) { status.text = "Ovoz aniqlanmadi (kod $error)"; recognizer.destroy() }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                recognizer.destroy()
                if (spoken.isNotBlank()) askBackend(spoken) else status.text = "Gap aniqlanmadi"
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun askBackend(userText: String) {
        answer.text = "Siz: $userText"
        val history = JSONArray(prefs.getString("history", "[]") ?: "[]")
        history.put(JSONObject().put("role", "user").put("content", userText))
        val json = JSONObject().put("message", userText).put("language", language).put("history", history)
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL.trimEnd('/') + "/chat")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { status.text = "AI serveriga ulanib bo‘lmadi. Server manzilini sozlash kerak." }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                val reply = try { JSONObject(body).optString("reply", "Javob olinmadi") } catch (_: Exception) { "Server javobi xato" }
                runOnUiThread {
                    answer.text = reply
                    status.text = "JARVIS javob berdi"
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                    history.put(JSONObject().put("role", "assistant").put("content", reply))
                    val trimmed = JSONArray()
                    val start = maxOf(0, history.length() - 16)
                    for (i in start until history.length()) trimmed.put(history.getJSONObject(i))
                    prefs.edit().putString("history", trimmed.toString()).apply()
                }
                response.close()
            }
        })
    }

    override fun onInit(statusCode: Int) { setTtsLanguage() }
    private fun setTtsLanguage() {
        if (!::tts.isInitialized) return
        val locale = when (language) {
            "ru-RU" -> Locale("ru", "RU")
            "en-US" -> Locale.US
            else -> Locale("uz", "UZ")
        }
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            status.text = "Tanlangan til uchun telefon TTS ovozi topilmadi"
        }
    }
    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
