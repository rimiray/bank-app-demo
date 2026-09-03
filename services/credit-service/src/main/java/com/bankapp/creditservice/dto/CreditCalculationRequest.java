package com.bankapp.creditservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditCalculationRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal requestedAmount;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal monthlyIncome;

    @NotNull
    @Min(1)
    @Max(120)
    private Integer termMonths;

    private BigDecimal aiCollateralValueEur;
}
