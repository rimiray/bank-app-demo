package com.bankapp.aicollateralservice.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollateralEvaluationResponse {

    private String objectDetected;
    private String condition;
    private BigDecimal estimatedValueEur;
    private BigDecimal maxCreditLimitEur;
}
