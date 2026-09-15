package com.bankapp.zbk.ui.credit

import com.bankapp.zbk.shared.dto.CreditCalculationResponse

sealed interface CreditUiState {
    /** Form ready; no calculation has been requested yet. */
    data object Idle : CreditUiState

    data object Loading : CreditUiState

    data class Success(
        val result: CreditCalculationResponse,
    ) : CreditUiState

    data class Error(
        val message: String,
    ) : CreditUiState
}
