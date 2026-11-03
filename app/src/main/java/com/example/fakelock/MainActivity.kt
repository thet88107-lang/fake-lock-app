package com.example.fakelock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen:
 *  - set an optional PIN used to dismiss the fake lock screen
 *  - grant "draw over other apps" permission (required for the overlay)
 *  - start / stop the prank
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pinInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pinInput = findViewById(R.id.pinInput)
        statusText = findViewById(R.id.statusText)

        val prefs = FakeLockApp.prefs(this)
        pinInput.setText(prefs.getString(FakeLockApp.KEY_PIN, ""))

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "لازم تدي صلاحية \"الظهور فوق التطبيقات\" الأول", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return@setOnClickListener
            }
            prefs.edit()
                .putString(FakeLockApp.KEY_PIN, pinInput.text.toString())
                .putBoolean(FakeLockApp.KEY_ENABLED, true)
                .apply()
            LockOverlayService.start(this)
            Toast.makeText(this, "اتفعّل القفل الوهمي", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            prefs.edit().putBoolean(FakeLockApp.KEY_ENABLED, false).apply()
            LockOverlayService.stop(this)
            Toast.makeText(this, "اتوقف", Toast.LENGTH_SHORT).show()
            updateStatus()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = FakeLockApp.prefs(this).getBoolean(FakeLockApp.KEY_ENABLED, false)
        val overlayOk = canDrawOverlays()
        statusText.text = buildString {
            append(if (overlayOk) "✅ صلاحية الأوفرلاي متاحة\n" else "❌ محتاج صلاحية الأوفرلاي\n")
            append(if (enabled) "✅ القفل الوهمي شغال" else "⏸️ القفل الوهمي متوقف")
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateStatus()
    }
}
