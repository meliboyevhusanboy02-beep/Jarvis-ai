package uz.jarvis.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService

class JarvisInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCall = call
        if (call.state == Call.STATE_RINGING) showIncomingCallNotification()
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_DISCONNECTED) {
                    activeCall = null
                    getSystemService(NotificationManager::class.java).cancel(2002)
                }
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        if (activeCall === call) activeCall = null
        getSystemService(NotificationManager::class.java).cancel(2002)
        super.onCallRemoved(call)
    }

    private fun showIncomingCallNotification() {
        val channelId = "jarvis_calls"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val fullScreen = PendingIntent.getActivity(
            this, 20, Intent(this, IncomingCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("JARVIS — kiruvchi qo‘ng‘iroq")
            .setContentText("Qo‘ng‘iroqni qabul qilish yoki rad etish")
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2002, notification)
    }

    companion object {
        @Volatile var activeCall: Call? = null
            private set

        fun answerActiveCall() {
            activeCall?.takeIf { it.state == Call.STATE_RINGING }?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
        }
        fun rejectActiveCall() {
            activeCall?.takeIf { it.state == Call.STATE_RINGING }?.reject(false, null)
        }
    }
}
