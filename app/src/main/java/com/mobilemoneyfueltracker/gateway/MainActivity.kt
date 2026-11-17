package com.mobilemoneyfueltracker.gateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mobilemoneyfueltracker.gateway.ui.GatewayApp
import com.mobilemoneyfueltracker.gateway.ui.SmsPermissionState

class MainActivity : ComponentActivity() {
    private fun smsGranted() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECEIVE_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val application = application as GatewayApplication
        setContent {
            var permission by remember {
                mutableStateOf(
                    if (smsGranted()) SmsPermissionState.GRANTED else SmsPermissionState.NOT_REQUESTED,
                )
            }
            // The user may grant or revoke the permission in system settings and come back.
            LifecycleResumeEffect(Unit) {
                if (smsGranted()) {
                    permission = SmsPermissionState.GRANTED
                } else if (permission == SmsPermissionState.GRANTED) {
                    permission = SmsPermissionState.NOT_REQUESTED
                }
                onPauseOrDispose { }
            }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                permission = when {
                    granted -> SmsPermissionState.GRANTED
                    // With no rationale left to show, Android will not prompt again.
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.RECEIVE_SMS,
                    ) -> SmsPermissionState.PERMANENTLY_DENIED
                    else -> SmsPermissionState.DENIED
                }
            }
            GatewayApp(
                application = application,
                smsPermission = permission,
                requestSmsPermission = { launcher.launch(Manifest.permission.RECEIVE_SMS) },
                openAppSettings = {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ),
                    )
                },
            )
        }
    }
}
