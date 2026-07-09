package com.example.reelblocker

import android.content.Intent
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

        findViewById<Button>(R.id.openSettingsButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = if (isServiceEnabled()) {
            "✅ Reel Blocker is ON.\nOpen Instagram — Reels will be swapped for the Home tab automatically."
        } else {
            "⚠️ Reel Blocker is OFF.\nTap below, find \"Reel Blocker\" in the list, and turn it on."
        }
    }

    private fun isServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${ReelBlockerService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }
}
