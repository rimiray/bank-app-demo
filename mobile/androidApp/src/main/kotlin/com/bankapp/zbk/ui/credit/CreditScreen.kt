package com.bankapp.zbk.ui.credit

import android.widget.Toast
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bankapp.zbk.shared.dto.CardResponse
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import com.bankapp.zbk.ui.cards.CardsUiState
import com.bankapp.zbk.ui.cards.CardsViewModel
import com.bankapp.zbk.ui.util.formatMoney
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    modifier: Modifier = Modifier,
    creditViewModel: CreditViewModel = koinViewModel(),
    cardsViewModel: CardsViewModel = koinViewModel(),
) {
    val state by creditViewModel.uiState.collectAsStateWithLifecycle()
    val applying by creditViewModel.applying.collectAsStateWithLifecycle()
    val cardsState by cardsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(creditViewModel) {
        creditViewModel.events.collect { event ->
            when (event) {
                CreditEvent.ApplySuccess -> {
                    Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
                    cardsViewModel.refresh()
                }
                is CreditEvent.ErrorMessage -> {
                    snackbarHostState.showSnackbar(event.text)
                }
            }
        }
    }

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

    val activeCards =
        when (val cards = cardsState) {
            is CardsUiState.Success ->
                cards.cards.filter { it.status.equals("ACTIVE", ignoreCase = true) }
            else -> emptyList()
        }

    val busy = state is CreditUiState.Loading || applying

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                enabled = !busy,
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
                enabled = !busy,
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
                enabled = !busy,
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
                    creditViewModel.calculateCredit(
                        CreditApplicationRequest(
                            requestedAmount = amountValue,
                            monthlyIncome = incomeValue,
                            termMonths = termValue,
                        ),
                    )
                },
                enabled = formReady && !busy,
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

                    if (ui.result.status.equals("APPROVED", ignoreCase = true)) {
                        ApplyCreditSection(
                            activeCards = activeCards,
                            applying = applying,
                            onApply = creditViewModel::applyCreditToSelectedCard,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplyCreditSection(
    activeCards: List<CardResponse>,
    applying: Boolean,
    onApply: (cardId: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedCardId by rememberSaveable {
        mutableStateOf(activeCards.firstOrNull()?.id.orEmpty())
    }

    LaunchedEffect(activeCards) {
        if (activeCards.none { it.id == selectedCardId }) {
            selectedCardId = activeCards.firstOrNull()?.id.orEmpty()
        }
    }

    val selectedLabel =
        activeCards
            .firstOrNull { it.id == selectedCardId }
            ?.let { "${it.cardNumberMasked} · ${formatMoney(it.balance, it.currency)}" }
            ?: "No active cards"

    Text(
        text = "Apply to card",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )

    if (activeCards.isEmpty()) {
        Text(
            text = "No active cards available. Issue or activate a card first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!applying) expanded = !expanded },
    ) {
        OutlinedTextField(
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            enabled = !applying,
            label = { Text("Target card") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            activeCards.forEach { card ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${card.cardNumberMasked} · bal ${formatMoney(card.balance, card.currency)}",
                        )
                    },
                    onClick = {
                        selectedCardId = card.id
                        expanded = false
                    },
                )
            }
        }
    }

    Button(
        onClick = { onApply(selectedCardId) },
        enabled = selectedCardId.isNotBlank() && !applying,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (applying) "Applying…" else "Apply to Selected Card")
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
