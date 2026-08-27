package com.example.driveapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

enum class DriveState(val label: String, val speech: String) {
    OFF("מעקב כבוי", "המעקב כבוי"),
    DRIVING("בנסיעה", "מצב נסיעה הופעל"),
    PARKED("בחנייה", "מצב חנייה הופעל")
}

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ttsManager = TtsManager(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onStateChange = { newState ->
                            ttsManager.speak(newState.speech)
                            updateService(newState.label)
                        },
                        onOpenParkingLocation = {
                            ttsManager.speak("פותח את מיקום החנייה האחרון")
                            val gmmIntentUri = Uri.parse("google.navigation:q=0,0")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            startActivity(mapIntent)
                        },
                        onOpenPango = {
                            ttsManager.speak("פותח את אפליקציית פנגו")
                            val pangoIntent = packageManager.getLaunchIntentForPackage("com.pango.activity")
                            if (pangoIntent != null) {
                                startActivity(pangoIntent)
                            } else {
                                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.pango.activity"))
                                startActivity(marketIntent)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun updateService(stateLabel: String) {
        val intent = Intent(this, DriveService::class.java).apply {
            putExtra(DriveService.EXTRA_STATE, stateLabel)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    onStateChange: (DriveState) -> Unit,
    onOpenParkingLocation: () -> Unit,
    onOpenPango: () -> Unit
) {
    var currentState by remember { mutableStateOf(DriveState.OFF) }

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
        // כפתור מעקב ראשי
        Button(
            onClick = {
                currentState = when (currentState) {
                    DriveState.OFF -> DriveState.DRIVING
                    DriveState.DRIVING -> DriveState.PARKED
                    DriveState.PARKED -> DriveState.OFF
                }
                onStateChange(currentState)
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
