package com.example.fakelock

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class FakeLockApp : Application() {

    companion object {
        const val PREFS = "fakelock_prefs"
        const val KEY_PIN = "pin"
        const val KEY_ENABLED = "enabled"

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
