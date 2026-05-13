package com.ledger.app.ui.ledger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class LedgerSmsReceiver(
  private val onSmsReceived: (sender: String, body: String) -> Unit,
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
    if (messages.isNullOrEmpty()) return

    val sender = messages.first().originatingAddress ?: "Unknown"
    val body = messages.joinToString("") { it.messageBody }

    if (body.isNotBlank()) {
      onSmsReceived(sender, body)
    }
  }
}
