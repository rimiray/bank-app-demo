package com.bankapp.aicollateralservice.controller;

import com.bankapp.aicollateralservice.dto.CollateralEvaluationResponse;
import com.bankapp.aicollateralservice.service.CollateralService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/collateral")
public class CollateralController {

    private final CollateralService collateralService;

    public CollateralController(CollateralService collateralService) {
        this.collateralService = collateralService;
    }

    @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CollateralEvaluationResponse evaluate(@RequestPart("file") MultipartFile file) {
        return collateralService.evaluate(file);
    }
}
