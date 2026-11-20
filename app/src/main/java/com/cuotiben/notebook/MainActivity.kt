package com.cuotiben.notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cuotiben.notebook.ui.AppRoot
import com.cuotiben.notebook.ui.theme.CuoTiBenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CuoTiBenTheme {
                AppRoot()
            }
        }
    }
}
