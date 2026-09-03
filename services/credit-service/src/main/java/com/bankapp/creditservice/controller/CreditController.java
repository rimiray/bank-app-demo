package com.bankapp.creditservice.controller;

import com.bankapp.creditservice.dto.CreditCalculationRequest;
import com.bankapp.creditservice.dto.CreditCalculationResponse;
import com.bankapp.creditservice.service.CreditService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @PostMapping("/calculate")
    public CreditCalculationResponse calculate(@Valid @RequestBody CreditCalculationRequest request) {
        return creditService.calculate(request);
    }
}
