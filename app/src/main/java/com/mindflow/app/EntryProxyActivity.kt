package com.mindflow.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

class EntryProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardToMainActivity(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forwardToMainActivity(intent)
    }

    private fun forwardToMainActivity(sourceIntent: Intent?) {
        val targetIntent = Intent(this, MainActivity::class.java).apply {
            action = sourceIntent?.action
            type = sourceIntent?.type
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            sourceIntent?.extras?.let(::putExtras)
        }
        startActivity(targetIntent)
        finish()
        overrideCloseTransition()
    }

    private fun overrideCloseTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
