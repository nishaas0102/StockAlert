package com.stockalert.controller;

import com.stockalert.dto.PageResponse;
import com.stockalert.dto.ReorderAlertResponseDTO;
import com.stockalert.service.ReorderAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reorder-alerts")
public class ReorderAlertController {

    private final ReorderAlertService alertService;

    public ReorderAlertController(ReorderAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReorderAlertResponseDTO>> getAlerts(
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(alertService.getAlerts(resolved, safePage, safeSize));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<ReorderAlertResponseDTO> resolveAlert(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.resolveAlert(id));
    }
}
