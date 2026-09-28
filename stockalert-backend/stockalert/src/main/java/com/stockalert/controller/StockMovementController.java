package com.stockalert.controller;

import com.stockalert.dto.PageResponse;
import com.stockalert.dto.StockMovementRequestDTO;
import com.stockalert.dto.StockMovementResponseDTO;
import com.stockalert.service.StockMovementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-movements")
public class StockMovementController {

    private final StockMovementService movementService;

    public StockMovementController(StockMovementService movementService) {
        this.movementService = movementService;
    }

    @PostMapping
    public ResponseEntity<StockMovementResponseDTO> recordMovement(
            @Valid @RequestBody StockMovementRequestDTO request) {
        return new ResponseEntity<>(movementService.recordMovement(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<PageResponse<StockMovementResponseDTO>> getMovements(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(movementService.getMovements(productId, safePage, safeSize));
    }
}
