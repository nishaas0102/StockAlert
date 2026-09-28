package com.stockalert.service;

import com.stockalert.dto.PageResponse;
import com.stockalert.dto.StockMovementRequestDTO;
import com.stockalert.dto.StockMovementResponseDTO;
import com.stockalert.exception.InsufficientStockException;
import com.stockalert.exception.ResourceNotFoundException;
import com.stockalert.model.MovementType;
import com.stockalert.model.Product;
import com.stockalert.model.StockMovement;
import com.stockalert.repository.ProductRepository;
import com.stockalert.repository.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockMovementService {

    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final ReorderAlertService alertService;

    public StockMovementService(StockMovementRepository movementRepository,
                                ProductRepository productRepository,
                                ReorderAlertService alertService) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.alertService = alertService;
    }

    @Transactional
    public StockMovementResponseDTO recordMovement(StockMovementRequestDTO request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id " + request.getProductId()));

        int newQuantity;
        if (request.getType() == MovementType.IN) {
            newQuantity = product.getQuantity() + request.getQuantity();
        } else {
            if (request.getQuantity() > product.getQuantity()) {
                throw new InsufficientStockException("Not enough stock for '" + product.getName()
                        + "'. Available: " + product.getQuantity() + ", requested: " + request.getQuantity());
            }
            newQuantity = product.getQuantity() - request.getQuantity();
        }

        product.setQuantity(newQuantity);
        productRepository.save(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(request.getType());
        movement.setQuantity(request.getQuantity());
        movement.setNote(request.getNote());
        StockMovement saved = movementRepository.save(movement);

        alertService.checkProduct(product);
        return toResponse(saved, newQuantity);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponseDTO> getMovements(Long productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> result;
        if (productId == null) {
            result = movementRepository.findAll(pageable);
        } else {
            if (!productRepository.existsById(productId)) {
                throw new ResourceNotFoundException("Product not found with id " + productId);
            }
            result = movementRepository.findByProductId(productId, pageable);
        }
        return new PageResponse<>(result.map(m -> toResponse(m, null)));
    }

    private StockMovementResponseDTO toResponse(StockMovement movement, Integer quantityAfter) {
        StockMovementResponseDTO dto = new StockMovementResponseDTO();
        dto.setId(movement.getId());
        dto.setProductId(movement.getProduct().getId());
        dto.setProductName(movement.getProduct().getName());
        dto.setType(movement.getType());
        dto.setQuantity(movement.getQuantity());
        dto.setNote(movement.getNote());
        dto.setQuantityAfter(quantityAfter);
        dto.setCreatedAt(movement.getCreatedAt());
        return dto;
    }
}
