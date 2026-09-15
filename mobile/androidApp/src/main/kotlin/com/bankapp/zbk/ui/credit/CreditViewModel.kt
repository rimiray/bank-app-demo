package com.bankapp.zbk.ui.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bankapp.zbk.shared.data.BankingRepository
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import com.bankapp.zbk.shared.dto.CreditCalculationResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CreditEvent {
    data object ApplySuccess : CreditEvent
}

class CreditViewModel(
    private val repository: BankingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CreditUiState>(CreditUiState.Idle)
    val uiState: StateFlow<CreditUiState> = _uiState.asStateFlow()

    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    private val _applyError = MutableStateFlow<String?>(null)
    val applyError: StateFlow<String?> = _applyError.asStateFlow()

    private val _events = MutableSharedFlow<CreditEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CreditEvent> = _events.asSharedFlow()

    private var lastRequest: CreditApplicationRequest? = null
    private var lastResult: CreditCalculationResponse? = null

    fun calculateCredit(request: CreditApplicationRequest) {
        viewModelScope.launch {
            _applyError.value = null
            _uiState.value = CreditUiState.Loading
            repository
                .calculateCredit(request)
                .onSuccess { result ->
                    lastRequest = request
                    lastResult = result
                    _uiState.value = CreditUiState.Success(result)
                }.onFailure { error ->
                    lastRequest = null
                    lastResult = null
                    _uiState.value =
                        CreditUiState.Error(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Failed to calculate credit",
                        )
                }
        }
    }

    fun applyCreditToSelectedCard(cardId: String) {
        val request = lastRequest
        val result = lastResult
        if (request == null || result == null) return
        if (!result.status.equals("APPROVED", ignoreCase = true)) {
            _applyError.value = "Credit was not approved"
            return
        }

        viewModelScope.launch {
            _applying.value = true
            _applyError.value = null
            repository
                .applyCreditToCard(
                    cardId = cardId,
                    amount = request.requestedAmount,
                    approvedLimit = result.approvedLimit,
                ).onSuccess {
                    _applying.value = false
                    _events.emit(CreditEvent.ApplySuccess)
                }.onFailure { error ->
                    _applying.value = false
                    _applyError.value =
                        error.message?.takeIf { it.isNotBlank() }
                            ?: "Failed to apply credit"
                }
        }
    }

    fun reset() {
        lastRequest = null
        lastResult = null
        _applyError.value = null
        _applying.value = false
        _uiState.value = CreditUiState.Idle
    }
}
