package com.bankapp.zbk.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bankapp.zbk.shared.data.BankingRepository
import com.bankapp.zbk.ui.util.ApiErrorMessages
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CardsViewModel(
    private val repository: BankingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CardsUiState>(CardsUiState.Loading)
    val uiState: StateFlow<CardsUiState> = _uiState.asStateFlow()

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

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
                    val message = ApiErrorMessages.from(error)
                    _uiState.value = CardsUiState.Error(message)
                    _userMessages.emit(message)
                }
        }
    }
}
