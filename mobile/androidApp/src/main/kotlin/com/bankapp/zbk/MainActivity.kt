package com.bankapp.zbk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bankapp.zbk.ui.cards.CardsScreen
import com.bankapp.zbk.ui.credit.CreditScreen
import com.bankapp.zbk.ui.navigation.ZbkDestination
import com.bankapp.zbk.ui.theme.ZbkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZbkTheme {
                ZbkCreditCompanionApp()
            }
        }
    }
}

@Composable
private fun ZbkCreditCompanionApp() {
    var destination by rememberSaveable { mutableStateOf(ZbkDestination.Cards) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                ZbkDestination.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination == tab,
                        onClick = { destination = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (destination) {
            ZbkDestination.Cards ->
                CardsScreen(modifier = Modifier.padding(innerPadding))
            ZbkDestination.Credit ->
                CreditScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}
