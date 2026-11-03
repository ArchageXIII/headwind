package com.archagexiii.headwind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.archagexiii.headwind.ui.HeadwindApp
import com.archagexiii.headwind.ui.theme.HeadwindTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeadwindTheme {
                HeadwindApp()
            }
        }
    }
}
