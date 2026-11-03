package com.example.fakelock

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * Anti-theft "fake off" screen.
 *
 * Draws a pure black, fully opaque window on top of everything (even the
 * launcher / other apps, and — where the OS allows it — the keyguard) the
 * instant the screen turns on. Visually the phone looks switched off /
 * locked. It only disappears when the exact volume-key pattern is entered:
 * DOWN, DOWN, DOWN, UP, UP (in that order, each press within a few seconds
 * of the last). Any other combination is swallowed — it does not change the
 * system volume and does not reveal anything.
 *
 * Apps and the service itself keep running in the background throughout;
 * only the display is covered.
 */
class LockOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "fakelock_channel"
        private const val NOTIF_ID = 42

        // The required sequence: 3x volume-down, then 2x volume-up.
        private val UNLOCK_PATTERN = listOf('D', 'D', 'D', 'U', 'U')

        // Max gap between consecutive presses before the sequence resets.
        private const val MAX_GAP_MS = 2500L

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

    private val pressLog = mutableListOf<Char>()
    private var lastPressTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val reapplyImmersive = object : Runnable {
        override fun run() {
            overlayView?.let { hideSystemBars(it) }
            handler.postDelayed(this, 1500)
        }
    }

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
            .setContentTitle("Security active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
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
        if (overlayView != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_lock, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // Deliberately focusable (no FLAG_NOT_FOCUSABLE) so this window
        // receives hardware key events — that's how the volume pattern is
        // intercepted before the system can act on it.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        view.setOnKeyListener { _, keyCode, event ->
            handleKeyEvent(keyCode, event)
        }
        view.isFocusableInTouchMode = true

        windowManager?.addView(view, params)
        overlayView = view
        hideSystemBars(view)
        view.post {
            view.requestFocus()
            hideSystemBars(view)
        }
        pressLog.clear()
        handler.removeCallbacks(reapplyImmersive)
        handler.postDelayed(reapplyImmersive, 1500)
    }

    /**
     * Hides the status bar and navigation bar and extends our black window
     * underneath them, so no system UI (clock, signal icons, gesture pill)
     * is visible anywhere on screen. On pre-R devices this is "immersive
     * sticky" — a swipe can reveal the bars briefly, which is why we keep
     * re-hiding on a short loop (see reapplyImmersive) rather than only once.
     */
    private fun hideSystemBars(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    /**
     * Returns true to consume the event (block the system default action,
     * e.g. changing volume or going back/home).
     */
    private fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Still consume UP/repeat for the keys we care about so nothing leaks through.
            return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_BACK
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> recordPress('D')
            KeyEvent.KEYCODE_VOLUME_UP -> recordPress('U')
            KeyEvent.KEYCODE_BACK -> return true // swallow back, do nothing
            else -> return false // let other keys behave normally
        }
        return true
    }

    private fun recordPress(symbol: Char) {
        val now = System.currentTimeMillis()
        if (now - lastPressTime > MAX_GAP_MS) {
            pressLog.clear()
        }
        lastPressTime = now

        pressLog.add(symbol)
        if (pressLog.size > UNLOCK_PATTERN.size) {
            pressLog.removeAt(0)
        }

        if (pressLog == UNLOCK_PATTERN) {
            pressLog.clear()
            unlock()
        }
    }

    /** Correct pattern entered: just hide the black cover. The service keeps
     * running and will re-cover the screen next time it turns off and on. */
    private fun unlock() {
        removeOverlay()
    }

    fun removeOverlay() {
        handler.removeCallbacks(reapplyImmersive)
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        pressLog.clear()
    }
}
