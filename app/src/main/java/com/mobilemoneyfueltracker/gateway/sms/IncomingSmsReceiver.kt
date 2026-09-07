package com.mobilemoneyfueltracker.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mobilemoneyfueltracker.gateway.GatewayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val sender = parts.first().originatingAddress ?: "UNKNOWN_SENDER"
        val app = context.applicationContext as GatewayApplication
        if (!TrustedSenderMatcher.isAllowed(sender, app.settingsRepository.settings.value.trustedSenders)) {
            return
        }
        val originalBody = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = parts.maxOf { it.timestampMillis }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // The receiver only performs a lossless local insert. Parsing and networking are delegated.
                app.queueRepository.persistIncoming(sender, originalBody, receivedAt)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
