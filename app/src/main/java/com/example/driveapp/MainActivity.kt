package com.example.driveapp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

enum class DriveState(val label: String, val speech: String) {
    OFF("מעקב כבוי", "המעקב כבוי"),
    DRIVING("בנסיעה", "מצב נסיעה הופעל"),
    PARKED("בחנייה", "מצב חנייה הופעל ושמור")
}

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager
    private var currentStateState = mutableStateOf(DriveState.OFF)

    // מקלט Bluetooth המזהה חיבור וניתוק מהרכב
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    updateState(DriveState.DRIVING)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    updateState(DriveState.PARKED)
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = TtsManager(this)

        requestRequiredPermissions()
        registerBluetoothReceiver()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        currentState = currentStateState.value,
                        onStateChange = { newState ->
                            updateState(newState)
                        },
                        onOpenParkingLocation = { openSavedParkingLocation() },
                        onOpenPango = { openPangoApp() }
                    )
                }
            }
        }
    }

    private fun updateState(newState: DriveState) {
        currentStateState.value = newState
        
        // אם עברנו למצב חנייה - שומרים מיקום GPS אוטומטית
        if (newState == DriveState.PARKED) {
            saveParkingLocation()
        }

        ttsManager.speak(newState.speech)
        updateService(newState.label)
    }

    private fun saveParkingLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (location != null) {
                val prefs = getSharedPreferences("DriveAppPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putFloat("parking_lat", location.latitude.toFloat())
                    .putFloat("parking_lng", location.longitude.toFloat())
                    .apply()
            }
        }
    }

    private fun openSavedParkingLocation() {
        val prefs = getSharedPreferences("DriveAppPrefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("parking_lat", 0f)
        val lng = prefs.getFloat("parking_lng", 0f)

        if (lat != 0f && lng != 0f) {
            ttsManager.speak("פותח את מיקום החנייה השמור")
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(מיקום חנייה)")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            startActivity(mapIntent)
        } else {
            ttsManager.speak("טרם נשמר מיקום חנייה")
        }
    }

    private fun openPangoApp() {
        ttsManager.speak("פותח את אפליקציית פנגו")
        val pangoPackage = "com.unicell.pangoandroid"
        val pangoIntent = packageManager.getLaunchIntentForPackage(pangoPackage)
        if (pangoIntent != null) {
            startActivity(pangoIntent)
        } else {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pangoPackage"))
            startActivity(marketIntent)
        }
    }

    private fun updateService(stateLabel: String) {
        val intent = Intent(this, DriveService::class.java).apply {
            putExtra(DriveService.EXTRA_STATE, stateLabel)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        ttsManager.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    currentState: DriveState,
    onStateChange: (DriveState) -> Unit,
    onOpenParkingLocation: () -> Unit,
    onOpenPango: () -> Unit
) {
    val buttonColor = when (currentState) {
        DriveState.OFF -> Color(0xFF757575)       // אפור
        DriveState.DRIVING -> Color(0xFF4CAF50)   // ירוק
        DriveState.PARKED -> Color(0xFF2196F3)    // כחול
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // כפתור מעקב מצבים
        Button(
            onClick = {
                val nextState = when (currentState) {
                    DriveState.OFF -> DriveState.DRIVING
                    DriveState.DRIVING -> DriveState.PARKED
                    DriveState.PARKED -> DriveState.OFF
                }
                onStateChange(nextState)
            },
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Text(
                text = "מצב מעקב: ${currentState.label}",
                fontSize = 20.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // כפתור מקום חנייה אחרון
        Button(
            onClick = onOpenParkingLocation,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(text = "פתח מקום חנייה אחרון", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // כפתור פתיחת פנגו
        Button(
            onClick = onOpenPango,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(text = "פתח אפליקציית Pango", fontSize = 16.sp, color = Color.White)
        }
    }
}
