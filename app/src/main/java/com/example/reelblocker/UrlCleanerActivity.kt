package com.example.reelblocker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class UrlCleanerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText.isNullOrBlank()) {
            finish()
            return
        }

        val cleaned = UrlCleaner.cleanText(sharedText)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("cleaned_url", cleaned))
        Toast.makeText(this, "Cleaned + copied", Toast.LENGTH_SHORT).show()

        finish()
    }
}
