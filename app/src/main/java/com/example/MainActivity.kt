package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.example.compass.CompassCameraScreen
import com.example.compass.CompassSensorManager
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var compassSensorManager: CompassSensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on for continuous viewfinder/compass navigation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extend video behind status bar and punch-hole on Samsung Galaxy S21
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        compassSensorManager = CompassSensorManager(this)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    CompassCameraScreen(compassSensorManager = compassSensorManager)
                }
            }
        }
    }
}
