package com.mobilemoneyfueltracker.gateway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mobilemoneyfueltracker.gateway.pairing.PairingCode
import com.mobilemoneyfueltracker.gateway.pairing.PairingResult
import kotlinx.coroutines.launch

internal fun pairingMessage(result: PairingResult): String = when (result) {
    is PairingResult.Success -> "Paired with ${result.stationName.ifBlank { "your station" }}."
    PairingResult.InvalidCode ->
        "That code is not valid. Check it, or ask your administrator for a new one. Codes expire after 15 minutes."
    PairingResult.TooManyAttempts -> "Too many attempts. Wait a minute and try again."
    PairingResult.NetworkError -> "Could not reach the server. Check your internet connection and try again."
    PairingResult.ServerError -> "Pairing failed on the server. Try again later."
}

/**
 * Pairs this phone using the one-time code an administrator generates on the dashboard.
 * Shown prominently until paired; afterwards it collapses to a "Re-pair" option.
 */
@Composable
fun PairingCard(
    paired: Boolean,
    stationName: String,
    onPair: suspend (String) -> PairingResult,
    onPaired: () -> Unit,
) {
    var expanded by remember { mutableStateOf(!paired) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (paired) {
                Text(
                    "Paired with ${stationName.ifBlank { "your station" }}",
                    fontWeight = FontWeight.Bold,
                )
                if (!expanded) {
                    TextButton(onClick = { expanded = true; message = null }) { Text("Re-pair this phone") }
                }
            } else {
                Text("Pair this phone", fontWeight = FontWeight.Bold)
                Text(
                    "Ask your station administrator for a pairing code (Gateways tab on the dashboard) and enter it below.",
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(PairingCode.LENGTH + 2) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pairing code") },
                    placeholder = { Text("ABCD-EFGH") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !busy && code.isNotBlank(),
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = onPair(code)
                            busy = false
                            failed = result !is PairingResult.Success
                            message = pairingMessage(result)
                            if (result is PairingResult.Success) {
                                code = ""
                                expanded = false
                                onPaired()
                            }
                        }
                    },
                ) { Text(if (busy) "Pairing…" else "Pair") }
                if (paired) {
                    TextButton(onClick = { expanded = false; message = null }) { Text("Cancel") }
                }
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = if (failed) Color(0xFF9B3229) else Color.Unspecified)
            }
        }
    }
}
