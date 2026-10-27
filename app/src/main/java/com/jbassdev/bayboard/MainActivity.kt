package com.jbassdev.bayboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jbassdev.bayboard.ui.BayBoardApp
import com.jbassdev.bayboard.ui.theme.BayBoardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BayBoardTheme {
                BayBoardApp()
            }
        }
    }
}
