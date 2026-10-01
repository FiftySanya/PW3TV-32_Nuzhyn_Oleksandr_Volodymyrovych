package com.example.energymonitoringapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.energymonitoringapp.composables.AuthScreen
import com.example.energymonitoringapp.composables.ErrorHandlingScreen
import com.example.energymonitoringapp.composables.RepositoryScreen
import com.example.energymonitoringapp.composables.RestApiScreen
import com.example.energymonitoringapp.ui.theme.EnergyMonitoringAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnergyMonitoringAppTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Triple("Репозиторій", Icons.Default.Storage, 0),
        Triple("REST API", Icons.Default.CloudSync, 1),
        Triple("Автентифікація", Icons.Default.LockPerson, 2),
        Triple("Помилки", Icons.Default.WarningAmber, 3)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title, fontSize = 10.sp) },
                        icon = { Icon(icon, contentDescription = title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RepositoryScreen()
                1 -> RestApiScreen()
                2 -> AuthScreen()
                3 -> ErrorHandlingScreen()
            }
        }
    }
}
