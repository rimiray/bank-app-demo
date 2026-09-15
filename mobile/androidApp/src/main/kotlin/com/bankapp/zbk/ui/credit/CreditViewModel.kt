package com.bankapp.zbk.ui.credit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bankapp.zbk.shared.data.BankingRepository
import com.bankapp.zbk.shared.dto.CreditApplicationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreditViewModel(
    private val repository: BankingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CreditUiState>(CreditUiState.Idle)
    val uiState: StateFlow<CreditUiState> = _uiState.asStateFlow()

    fun calculateCredit(request: CreditApplicationRequest) {
        viewModelScope.launch {
            _uiState.value = CreditUiState.Loading
            repository
                .calculateCredit(request)
                .onSuccess { result ->
                    _uiState.value = CreditUiState.Success(result)
                }.onFailure { error ->
                    _uiState.value =
                        CreditUiState.Error(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Failed to calculate credit",
                        )
                }
        }
    }

    fun reset() {
        _uiState.value = CreditUiState.Idle
    }
}
