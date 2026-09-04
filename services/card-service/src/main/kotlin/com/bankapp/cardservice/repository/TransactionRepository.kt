package com.bankapp.cardservice.repository

import com.bankapp.cardservice.domain.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionRepository : JpaRepository<Transaction, String> {
    fun deleteByCardId(cardId: String)
}
