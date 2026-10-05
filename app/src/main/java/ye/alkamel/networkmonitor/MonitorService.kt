package ye.alkamel.networkmonitor

import android.app.*
import android.content.*
import android.os.*
import java.util.concurrent.Executors

class MonitorService : Service() {
    private val ex = Executors.newFixedThreadPool(16)
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            100,
            Notification.Builder(this, "monitor")
                .setContentTitle("مراقبة شبكة الكامل")
                .setContentText("المراقبة الخلفية مفعلة")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build()
        )
        h.post(task)
    }

    private val task = object : Runnable {
        override fun run() {
            h.postDelayed(this, 60000)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("monitor", "مراقبة الشبكة", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        ex.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}
