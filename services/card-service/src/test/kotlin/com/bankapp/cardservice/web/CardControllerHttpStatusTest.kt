package com.bankapp.cardservice.web

import com.bankapp.cardservice.controller.CardController
import com.bankapp.cardservice.exception.CannotCloseCardException
import com.bankapp.cardservice.exception.GlobalExceptionHandler
import com.bankapp.cardservice.exception.InsufficientFundsException
import com.bankapp.cardservice.service.CardService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@WebMvcTest(controllers = [CardController::class])
@Import(GlobalExceptionHandler::class)
class CardControllerHttpStatusTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var cardService: CardService

    @Test
    fun should_return402_whenBalanceAndLimitInsufficient() {
        every { cardService.purchase("card-1", BigDecimal("9999.00")) } throws InsufficientFundsException()

        mockMvc.perform(
            post("/api/v1/cards/card-1/purchase")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":9999.00}"""),
        )
            .andExpect(status().isPaymentRequired)
            .andExpect(jsonPath("$.status").value(402))
    }

    @Test
    fun should_return400_whenCloseRejectedDueToActiveDebt() {
        every { cardService.closeCard("card-2") } throws CannotCloseCardException()

        mockMvc.perform(post("/api/v1/cards/card-2/close"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }
}
