package com.bankapp.cardservice.exception

class CardNotFoundException(cardId: String) : RuntimeException("Card not found: $cardId")

class InsufficientFundsException : RuntimeException("Payment Required - Insufficient funds")

class CannotCloseCardException : RuntimeException("Cannot close card with active debt")
