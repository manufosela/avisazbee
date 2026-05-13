package com.manufosela.avisazbee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manufosela.avisazbee.app.AvisazbeeApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity. Compose owns the entire UI tree, navigation included.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AvisazbeeApp()
        }
    }
}
