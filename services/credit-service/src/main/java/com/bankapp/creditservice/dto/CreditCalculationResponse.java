package com.bankapp.creditservice.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCalculationResponse {

    private BigDecimal monthlyPayment;
    private BigDecimal interestRate;
    private BigDecimal approvedLimit;
    private String status;
}
