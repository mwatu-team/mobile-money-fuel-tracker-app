package com.mobilemoneyfueltracker.gateway

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.mobilemoneyfueltracker.gateway.ui.GatewayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as GatewayApplication
        setContent {
            var permissionGranted by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS,
                    ) == PackageManager.PERMISSION_GRANTED,
                )
            }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted -> permissionGranted = granted }
            GatewayApp(
                application = application,
                smsPermissionGranted = permissionGranted,
                requestSmsPermission = { launcher.launch(Manifest.permission.RECEIVE_SMS) },
            )
        }
    }
}
