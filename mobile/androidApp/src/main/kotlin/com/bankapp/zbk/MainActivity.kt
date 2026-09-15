package com.bankapp.zbk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankapp.zbk.ui.cards.CardsUiState
import com.bankapp.zbk.ui.cards.CardsViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZbkCreditCompanionApp()
        }
    }
}

@Composable
private fun ZbkCreditCompanionApp(cardsViewModel: CardsViewModel = koinViewModel()) {
    val cardsState by cardsViewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        when (val state = cardsState) {
                            CardsUiState.Loading -> "ZBK Credit Companion · loading cards…"
                            is CardsUiState.Success ->
                                "ZBK Credit Companion · ${state.cards.size} card(s)"
                            is CardsUiState.Error -> "ZBK Credit Companion · ${state.message}"
                        },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}
