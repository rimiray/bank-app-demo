package com.bankapp.creditservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCalculatedEvent {

    private UUID applicationId;
    private BigDecimal requestedAmount;
    private BigDecimal monthlyPayment;
    private BigDecimal interestRate;
    private BigDecimal approvedLimit;
    private String status;
    private Instant createdAt;
}
