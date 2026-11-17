package com.mobilemoneyfueltracker.gateway.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobilemoneyfueltracker.gateway.BuildConfig

enum class SmsPermissionState { NOT_REQUESTED, GRANTED, DENIED, PERMANENTLY_DENIED }

/**
 * Prominent disclosure shown before Android's own SMS permission prompt, as Google Play requires.
 * It must say what is read, why, where it goes, and link to the privacy policy.
 */
@Composable
fun SmsDisclosureDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMS access") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("PumpSync Gateway reads incoming text messages (SMS) on this phone so your station can confirm customer payments.")
                Text("• Only messages from the trusted senders you add in Settings are read. Every other message is ignored.")
                Text(
                    "• For each trusted message, the sender, the full text and the time received are sent over HTTPS " +
                        "to your station's PumpSync server and shown to your station's staff. This includes any " +
                        "customer name and phone number in the message.",
                )
                Text("• It is used only to verify mobile-money payments against fuel sales. It is not used for advertising and is not sold.")
                Text("• PumpSync is not affiliated with MTN, Airtel or any mobile-network operator, and never asks for a PIN.")
                if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)),
                                )
                            }
                        },
                    ) { Text("Read the privacy policy") }
                }
                Text("Next, Android will ask you to allow SMS access.", fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Status-screen card for the SMS permission: asks, explains a denial, or points at system settings. */
@Composable
fun SmsPermissionCard(
    state: SmsPermissionState,
    requestPermission: () -> Unit,
    openAppSettings: () -> Unit,
) {
    if (state == SmsPermissionState.GRANTED) return
    var showDisclosure by remember { mutableStateOf(false) }
    if (showDisclosure) {
        SmsDisclosureDialog(
            onContinue = {
                showDisclosure = false
                requestPermission()
            },
            onDismiss = { showDisclosure = false },
        )
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                SmsPermissionState.NOT_REQUESTED -> {
                    Text("SMS access is required to capture incoming merchant notifications.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showDisclosure = true }) { Text("Allow SMS reception") }
                }
                SmsPermissionState.DENIED -> {
                    Text(
                        "SMS access was not allowed, so PumpSync cannot capture payments. " +
                            "Nothing is read from your messages until you allow it.",
                        color = Color(0xFF9B3229),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showDisclosure = true }) { Text("Review and allow") }
                }
                SmsPermissionState.PERMANENTLY_DENIED -> {
                    Text(
                        "SMS access is turned off for PumpSync. To capture payments, open the app " +
                            "settings, choose Permissions, then allow SMS.",
                        color = Color(0xFF9B3229),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row { Button(onClick = openAppSettings) { Text("Open app settings") } }
                }
                SmsPermissionState.GRANTED -> Unit
            }
        }
    }
}
