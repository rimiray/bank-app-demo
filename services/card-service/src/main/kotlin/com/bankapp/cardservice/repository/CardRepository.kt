package com.bankapp.cardservice.repository

import com.bankapp.cardservice.domain.Card
import org.springframework.data.jpa.repository.JpaRepository

interface CardRepository : JpaRepository<Card, String>
