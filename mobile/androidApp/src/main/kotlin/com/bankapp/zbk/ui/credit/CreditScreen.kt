package com.bankapp.zbk.ui.credit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import com.bankapp.zbk.ui.util.formatMoney
import org.koin.androidx.compose.koinViewModel

@Composable
fun CreditScreen(
    modifier: Modifier = Modifier,
    viewModel: CreditViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var amount by rememberSaveable { mutableStateOf("10000") }
    var income by rememberSaveable { mutableStateOf("3500") }
    var term by rememberSaveable { mutableStateOf("24") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }

    val amountValue = amount.toDoubleOrNull()
    val incomeValue = income.toDoubleOrNull()
    val termValue = term.toIntOrNull()
    val formReady =
        amountValue != null &&
            amountValue > 0 &&
            incomeValue != null &&
            incomeValue > 0 &&
            termValue != null &&
            termValue in 1..120

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Credit calculator",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Enter amount, monthly income and term (1–120 months).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
                formError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Requested amount (EUR)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = income,
            onValueChange = {
                income = it
                formError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monthly income (EUR)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = term,
            onValueChange = {
                term = it
                formError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Term (months)") },
            supportingText = { Text("Must be between 1 and 120") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = term.isNotBlank() && termValue !in 1..120,
        )

        formError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = {
                if (!formReady || amountValue == null || incomeValue == null || termValue == null) {
                    formError = "Enter a valid amount, income and term (1–120)."
                    return@Button
                }
                formError = null
                viewModel.calculateCredit(
                    CreditApplicationRequest(
                        requestedAmount = amountValue,
                        monthlyIncome = incomeValue,
                        termMonths = termValue,
                    ),
                )
            },
            enabled = formReady && state !is CreditUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state is CreditUiState.Loading) "Calculating…" else "Calculate")
        }

        when (val ui = state) {
            CreditUiState.Idle -> {
                Text(
                    text = "Results will appear here after Calculate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CreditUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is CreditUiState.Error -> {
                Text(
                    text = ui.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            is CreditUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Verdict · ${ui.result.status}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        ResultRow(
                            label = "Monthly payment",
                            value = formatMoney(ui.result.monthlyPayment),
                        )
                        ResultRow(
                            label = "Approved limit",
                            value = formatMoney(ui.result.approvedLimit),
                        )
                        ResultRow(
                            label = "Interest rate",
                            value = "${ui.result.interestRate} %",
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
