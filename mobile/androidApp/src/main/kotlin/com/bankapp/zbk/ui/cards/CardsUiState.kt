package com.bankapp.zbk.ui.cards

import com.bankapp.zbk.shared.dto.CardResponse

sealed interface CardsUiState {
    data object Loading : CardsUiState

    data class Success(
        val cards: List<CardResponse>,
    ) : CardsUiState

    data class Error(
        val message: String,
    ) : CardsUiState
}
