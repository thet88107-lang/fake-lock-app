package com.example.fakelock

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.*

/**
 * Keeps a full-screen "lock screen" view drawn on top of everything using
 * TYPE_APPLICATION_OVERLAY. ScreenStateReceiver calls show()/refresh() again
 * the instant the screen turns back on, so the real power button still works
 * (it only controls the physical display) but the user always lands back on
 * this fake lock screen.
 */
class LockOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "fakelock_channel"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, LockOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockOverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("القفل الوهمي شغال")
            .setContentText("افتح التطبيق عشان توقفه")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fake Lock", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun showOverlay() {
        if (overlayView != null) {
            refreshClock()
            return
        }
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_lock, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // Deliberately NOT FLAG_NOT_FOCUSABLE: the overlay stays focusable so it
        // also captures the back button (it just does nothing with it).
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val pinSection = view.findViewById<View>(R.id.pinSection)
        val pinInput = view.findViewById<EditText>(R.id.unlockPinInput)
        val swipeHint = view.findViewById<View>(R.id.swipeHint)

        val savedPin = FakeLockApp.prefs(this).getString(FakeLockApp.KEY_PIN, "")

        view.setOnClickListener {
            if (!savedPin.isNullOrEmpty()) {
                pinSection.visibility = View.VISIBLE
                swipeHint.visibility = View.GONE
            } else {
                // No PIN set: tapping just acts as a normal "swipe to unlock" prank step.
            }
        }

        view.findViewById<View>(R.id.unlockButton).setOnClickListener {
            if (savedPin.isNullOrEmpty() || pinInput.text.toString() == savedPin) {
                stopSelfAndDisable()
            } else {
                Toast.makeText(this, "غلط", Toast.LENGTH_SHORT).show()
                pinInput.text.clear()
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
        refreshClock()
    }

    private fun stopSelfAndDisable() {
        FakeLockApp.prefs(this).edit().putBoolean(FakeLockApp.KEY_ENABLED, false).apply()
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun refreshClock() {
        overlayView?.let {
            val clock = it.findViewById<TextView>(R.id.clockText)
            val date = it.findViewById<TextView>(R.id.dateText)
            val now = Calendar.getInstance().time
            clock.text = DateFormat.format("hh:mm a", now)
            date.text = DateFormat.format("EEEE, d MMMM", now)
        }
    }

    fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }
}
