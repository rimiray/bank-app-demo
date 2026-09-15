package com.bankapp.zbk.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bankapp.zbk.shared.data.BankingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CardsViewModel(
    private val repository: BankingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CardsUiState>(CardsUiState.Loading)
    val uiState: StateFlow<CardsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CardsUiState.Loading
            repository
                .getCards()
                .onSuccess { cards ->
                    _uiState.value = CardsUiState.Success(cards)
                }.onFailure { error ->
                    _uiState.value =
                        CardsUiState.Error(
                            error.message?.takeIf { it.isNotBlank() } ?: "Failed to load cards",
                        )
                }
        }
    }
}
