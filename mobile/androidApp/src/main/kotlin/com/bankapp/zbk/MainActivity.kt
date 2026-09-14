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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bankapp.zbk.shared.data.BankingRepository
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val bankingRepository: BankingRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch the injected repository so Koin wiring is verified at startup.
        checkNotNull(bankingRepository)
        setContent {
            ZbkCreditCompanionApp()
        }
    }
}

@Composable
private fun ZbkCreditCompanionApp() {
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
                    text = "ZBK Credit Companion",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
    }
}
