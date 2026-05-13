package com.manufosela.avisazbee.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.manufosela.avisazbee.app.navigation.AvisazbeeNavHost
import com.manufosela.avisazbee.app.theme.AvisazbeeTheme

@Composable
fun AvisazbeeApp() {
    AvisazbeeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AvisazbeeNavHost()
        }
    }
}
