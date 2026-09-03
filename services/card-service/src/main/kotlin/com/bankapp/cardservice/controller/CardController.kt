package com.bankapp.cardservice.controller

import com.bankapp.cardservice.dto.AmountRequest
import com.bankapp.cardservice.dto.CardResponse
import com.bankapp.cardservice.service.CardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/cards")
class CardController(
    private val cardService: CardService,
) {
    @GetMapping
    fun getUserCards(): List<CardResponse> = cardService.getAllCards()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun issueCard(): CardResponse = cardService.issueCard()

    @PostMapping("/{cardId}/topup")
    fun topUpCard(
        @PathVariable cardId: String,
        @Valid @RequestBody request: AmountRequest,
    ): CardResponse = cardService.topUp(cardId, request.amount)

    @PostMapping("/{cardId}/purchase")
    fun purchaseCard(
        @PathVariable cardId: String,
        @Valid @RequestBody request: AmountRequest,
    ): CardResponse = cardService.purchase(cardId, request.amount)

    @PostMapping("/{cardId}/close")
    fun closeCard(@PathVariable cardId: String): CardResponse =
        cardService.closeCard(cardId)
}
