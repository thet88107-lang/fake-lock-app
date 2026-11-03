package com.example.fakelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires on SCREEN_ON / SCREEN_OFF / BOOT_COMPLETED. As long as the prank is
 * "enabled" in prefs, it (re)starts the overlay service every time the
 * screen turns on — so no matter how many times the power button is
 * pressed, the fake lock screen is what the user sees.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = FakeLockApp.prefs(context).getBoolean(FakeLockApp.KEY_ENABLED, false)
        if (!enabled) return

        when (intent.action) {
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_BOOT_COMPLETED -> {
                LockOverlayService.start(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                // no-op: service keeps running, overlay just isn't visible
                // while the physical screen is off.
            }
        }
    }
}
