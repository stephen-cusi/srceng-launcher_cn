package me.nillerusr

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import com.valvesoftware.source.R

@Suppress("DEPRECATION")
open class UpdateService : Service() {
    @JvmField var nm: NotificationManager? = null
    @JvmField var extras: Bundle? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!serviceWork) {
            serviceWork = true
            try {
                extras = intent?.extras
                sendNotification()
            } catch (_: Exception) {
            }
        }
        return START_NOT_STICKY
    }

    private fun sendNotification() {
        val notification = Notification(R.drawable.ic_launcher, "Update avalible", System.currentTimeMillis())
        notification.contentView = RemoteViews(packageName, R.layout.update_notify)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(extras!!["update_url"]!!.toString()))
        notification.contentIntent = PendingIntent.getActivity(this, 0, browserIntent, 0)
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
        notification.defaults = notification.defaults or Notification.DEFAULT_LIGHTS
        notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
        notification.defaults = notification.defaults or Notification.DEFAULT_SOUND
        notification.priority = notification.priority or Notification.PRIORITY_HIGH
        nm!!.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @JvmField
        var service_work = false

        private var serviceWork: Boolean
            get() = service_work
            set(value) {
                service_work = value
            }
    }
}
