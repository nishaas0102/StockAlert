package com.stockalert.service;

import com.stockalert.dto.PageResponse;
import com.stockalert.dto.ProductRequestDTO;
import com.stockalert.dto.ProductResponseDTO;
import com.stockalert.exception.DuplicateResourceException;
import com.stockalert.exception.ResourceNotFoundException;
import com.stockalert.model.Product;
import com.stockalert.repository.ProductRepository;
import com.stockalert.repository.ReorderAlertRepository;
import com.stockalert.repository.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class ProductService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "sku", "category", "quantity", "reorderLevel", "price", "createdAt");

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final ReorderAlertRepository alertRepository;
    private final ReorderAlertService alertService;

    public ProductService(ProductRepository productRepository,
                          StockMovementRepository movementRepository,
                          ReorderAlertRepository alertRepository,
                          ReorderAlertService alertService) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.alertRepository = alertRepository;
        this.alertService = alertService;
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDTO request) {
        String sku = request.getSku().trim();
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with SKU '" + sku + "' already exists");
        }
        Product product = new Product();
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        alertService.checkProduct(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        return toResponse(findProduct(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDTO> getProducts(String keyword, String category, boolean lowStockOnly,
                                                        int page, int size, String sortBy, String sortDir) {
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, field));

        String search = (keyword == null) ? "" : keyword.trim();
        Page<Product> result;
        if (lowStockOnly) {
            result = productRepository.findLowStock(pageable);
        } else if (category != null && !category.isBlank()) {
            result = productRepository.searchByCategory(search, category.trim(), pageable);
        } else {
            result = productRepository.search(search, pageable);
        }
        return new PageResponse<>(result.map(this::toResponse));
    }

    @Transactional
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO request) {
        Product product = findProduct(id);
        String sku = request.getSku().trim();
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateResourceException("A product with SKU '" + sku + "' already exists");
        }
        applyRequest(product, request);
        Product saved = productRepository.save(product);
        alertService.checkProduct(saved);
        return toResponse(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        alertRepository.deleteByProductId(id);
        movementRepository.deleteByProductId(id);
        productRepository.delete(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
    }

    private void applyRequest(Product product, ProductRequestDTO request) {
        product.setName(request.getName().trim());
        product.setSku(request.getSku().trim());
        product.setCategory(request.getCategory() == null ? null : request.getCategory().trim());
        product.setQuantity(request.getQuantity());
        product.setReorderLevel(request.getReorderLevel());
        product.setPrice(request.getPrice());
    }

    private ProductResponseDTO toResponse(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setSku(product.getSku());
        dto.setCategory(product.getCategory());
        dto.setQuantity(product.getQuantity());
        dto.setReorderLevel(product.getReorderLevel());
        dto.setPrice(product.getPrice());
        dto.setLowStock(product.getQuantity() <= product.getReorderLevel());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        return dto;
    }
}
