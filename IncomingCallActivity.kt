package uz.jarvis.ai

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class IncomingCallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply { text = "JARVIS — Kiruvchi qo‘ng‘iroq"; textSize = 24f }
        val answer = Button(this).apply {
            text = "Qabul qilish"
            setOnClickListener {
                JarvisInCallService.answerActiveCall()
                finish()
            }
        }
        val reject = Button(this).apply {
            text = "Rad etish"
            setOnClickListener {
                JarvisInCallService.rejectActiveCall()
                finish()
            }
        }
        layout.addView(title); layout.addView(answer); layout.addView(reject)
        setContentView(layout)
    }
}
