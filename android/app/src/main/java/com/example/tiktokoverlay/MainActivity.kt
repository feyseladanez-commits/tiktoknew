package com.example.tiktokoverlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Restore saved login token, if any
        val prefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        ApiClient.authToken = prefs.getString("token", null)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                tvStatus.text = "Overlay permission already granted."
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (ApiClient.authToken.isNullOrEmpty()) {
                tvStatus.text = "Please log in first."
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                tvStatus.text = "Please grant the overlay permission first."
                return@setOnClickListener
            }
            if (!isAccessibilityServiceEnabled()) {
                tvStatus.text = "Please enable the accessibility service first."
                return@setOnClickListener
            }
            startService(Intent(this, OverlayService::class.java))
            tvStatus.text = "Bubble started — open TikTok to see it."
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/.TikTokAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
