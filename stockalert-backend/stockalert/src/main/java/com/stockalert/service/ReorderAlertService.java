package com.stockalert.service;

import com.stockalert.dto.PageResponse;
import com.stockalert.dto.ReorderAlertResponseDTO;
import com.stockalert.exception.ResourceNotFoundException;
import com.stockalert.model.Product;
import com.stockalert.model.ReorderAlert;
import com.stockalert.repository.ReorderAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReorderAlertService {

    private final ReorderAlertRepository alertRepository;

    public ReorderAlertService(ReorderAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    /**
     * Called after a product's quantity or reorder level changes.
     * Creates/updates an open alert when stock is low, resolves open alerts when stock is OK.
     * The product must already be saved (must have an id).
     */
    @Transactional
    public void checkProduct(Product product) {
        List<ReorderAlert> openAlerts = alertRepository.findByProductIdAndResolvedFalse(product.getId());
        boolean low = product.getQuantity() <= product.getReorderLevel();

        if (low) {
            if (openAlerts.isEmpty()) {
                ReorderAlert alert = new ReorderAlert();
                alert.setProduct(product);
                alert.setCurrentQuantity(product.getQuantity());
                alert.setReorderLevel(product.getReorderLevel());
                alertRepository.save(alert);
            } else {
                for (ReorderAlert alert : openAlerts) {
                    alert.setCurrentQuantity(product.getQuantity());
                    alert.setReorderLevel(product.getReorderLevel());
                    alertRepository.save(alert);
                }
            }
        } else {
            for (ReorderAlert alert : openAlerts) {
                markResolved(alert);
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ReorderAlertResponseDTO> getAlerts(Boolean resolved, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReorderAlert> result;
        if (resolved == null) {
            result = alertRepository.findAll(pageable);
        } else {
            result = alertRepository.findByResolved(resolved, pageable);
        }
        return new PageResponse<>(result.map(this::toResponse));
    }

    @Transactional
    public ReorderAlertResponseDTO resolveAlert(Long id) {
        ReorderAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reorder alert not found with id " + id));
        if (!alert.isResolved()) {
            markResolved(alert);
        }
        return toResponse(alert);
    }

    private void markResolved(ReorderAlert alert) {
        alert.setResolved(true);
        alert.setResolvedAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    private ReorderAlertResponseDTO toResponse(ReorderAlert alert) {
        ReorderAlertResponseDTO dto = new ReorderAlertResponseDTO();
        dto.setId(alert.getId());
        dto.setProductId(alert.getProduct().getId());
        dto.setProductName(alert.getProduct().getName());
        dto.setSku(alert.getProduct().getSku());
        dto.setCurrentQuantity(alert.getCurrentQuantity());
        dto.setReorderLevel(alert.getReorderLevel());
        dto.setResolved(alert.isResolved());
        dto.setCreatedAt(alert.getCreatedAt());
        dto.setResolvedAt(alert.getResolvedAt());
        return dto;
    }
}
