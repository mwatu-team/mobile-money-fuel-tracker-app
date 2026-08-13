package com.mobilemoneyfueltracker.gateway.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mobilemoneyfueltracker.gateway.BuildConfig
import com.mobilemoneyfueltracker.gateway.GatewayApplication
import com.mobilemoneyfueltracker.gateway.data.GatewaySettings
import com.mobilemoneyfueltracker.gateway.data.LocalSmsMessage
import java.text.DateFormat
import java.util.Date

private enum class GatewayScreen(val label: String) { STATUS("Status"), MESSAGES("Messages"), SETTINGS("Settings") }
private val brand = Color(0xFF0B372F)
private val accent = Color(0xFFE9B44C)

@Composable
fun GatewayApp(
    application: GatewayApplication,
    smsPermissionGranted: Boolean,
    requestSmsPermission: () -> Unit,
) {
    var screen by remember { mutableStateOf(GatewayScreen.STATUS) }
    val messages by application.queueRepository.messages.collectAsState(initial = emptyList())
    val pendingCount by application.queueRepository.pendingCount.collectAsState(initial = 0)
    val lastReceivedAt by application.queueRepository.lastReceivedAt.collectAsState(initial = null)
    val settings by application.settingsRepository.settings.collectAsState()
    MaterialTheme {
        Scaffold(
            topBar = {
                Column(Modifier.fillMaxWidth().background(brand).padding(20.dp)) {
                    Text("PumpSync Gateway", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Offline SMS capture and synchronisation", color = Color(0xFFCDE0D9))
                }
            },
            bottomBar = {
                NavigationBar {
                    GatewayScreen.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { screen = destination },
                            icon = { Text(destination.label.take(1), fontWeight = FontWeight.Bold) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (screen) {
                    GatewayScreen.STATUS -> StatusScreen(settings, pendingCount, lastReceivedAt, smsPermissionGranted, requestSmsPermission)
                    GatewayScreen.MESSAGES -> MessagesScreen(messages, settings.parserDebugMode)
                    GatewayScreen.SETTINGS -> SettingsScreen(settings) {
                        application.settingsRepository.update(it)
                        application.queueRepository.enqueueSync()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(
    settings: GatewaySettings,
    pendingCount: Int,
    lastReceivedAt: Long?,
    permissionGranted: Boolean,
    requestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val connected = remember { hasNetwork(context) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Gateway status", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (!permissionGranted) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text("SMS access is required to capture incoming merchant notifications.")
                Spacer(Modifier.height(8.dp)); Button(onClick = requestPermission) { Text("Allow SMS reception") }
            } }
        }
        item { StatusCard("Device ID", settings.deviceIdentifier.ifBlank { "Not configured" }) }
        item { StatusCard("Backend connection", if (connected) "Network available" else "Offline") }
        item { StatusCard("Last SMS received", formatTime(lastReceivedAt)) }
        item { StatusCard("Pending uploads", pendingCount.toString()) }
        item { StatusCard("Last successful synchronisation", formatTime(settings.lastSuccessfulSyncEpochMillis)) }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.DarkGray); Text(value, fontWeight = FontWeight.Bold)
    } }
}

@Composable
private fun MessagesScreen(messages: List<LocalSmsMessage>, debugMode: Boolean) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Received messages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(messages, key = { it.id }) { message ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        message.customerName ?: maskPhone(message.customerPhone),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(message.syncStatus.name, color = if (message.syncStatus.name == "SYNCHRONISED") brand else accent)
                }
                Text(message.parsedTransactionType ?: "Awaiting parser")
                if (BuildConfig.DEBUG && debugMode) {
                    message.parseError?.let { Text("Parser: $it", color = Color(0xFF9B3229)) }
                    Text(message.originalBody.take(140), color = Color.Gray)
                }
                message.lastUploadError?.let { Text("Upload: $it", color = Color(0xFF9B3229)) }
            } }
        }
        if (messages.isEmpty()) item { Text("No SMS messages captured yet.") }
    }
}

@Composable
private fun SettingsScreen(initial: GatewaySettings, onSave: (GatewaySettings) -> Unit) {
    var edited by remember { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initial) { edited = initial }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(edited.backendBaseUrl, { edited = edited.copy(backendBaseUrl = it) }, Modifier.fillMaxWidth(), label = { Text("Backend base URL") }) }
        item { OutlinedTextField(edited.stationId, { edited = edited.copy(stationId = it) }, Modifier.fillMaxWidth(), label = { Text("Development station ID") }) }
        item { OutlinedTextField(edited.deviceIdentifier, { edited = edited.copy(deviceIdentifier = it) }, Modifier.fillMaxWidth(), label = { Text("Device ID") }) }
        item { OutlinedTextField(edited.developmentDeviceToken, { edited = edited.copy(developmentDeviceToken = it) }, Modifier.fillMaxWidth(), label = { Text("Development device token") }, visualTransformation = PasswordVisualTransformation()) }
        item { Row { Checkbox(edited.parserDebugMode, { edited = edited.copy(parserDebugMode = it) }); Text("Parser debug mode", Modifier.padding(top = 12.dp)) } }
        item { Button(onClick = { runCatching { onSave(edited) }.onSuccess { message = "Settings saved; queued messages will retry." }.onFailure { message = "Enter an HTTP or HTTPS backend URL." } }) { Text("Save settings") } }
        message?.let { value -> item { Text(value) } }
        item { Text("Development credentials only. Production device authentication must replace this mechanism.", color = Color.Gray) }
    }
}

private fun maskPhone(phone: String?): String = when {
    phone.isNullOrBlank() -> "Phone unavailable"
    phone.length < 7 -> "***"
    else -> "${phone.take(3)}***${phone.takeLast(3)}"
}
private fun formatTime(epochMillis: Long?): String = epochMillis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Never"
private fun hasNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
