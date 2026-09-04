package com.bankapp.cardservice.repository

import com.bankapp.cardservice.domain.Card
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<Card, String> {
    /**
     * Same order as a plain `SELECT * FROM cards` heap scan in Postgres
     * (what most SQL consoles show without an ORDER BY).
     */
    @Query(value = "SELECT * FROM cards ORDER BY ctid", nativeQuery = true)
    fun findAllInDbTableOrder(): List<Card>
}
