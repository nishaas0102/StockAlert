package com.stockalert.repository;

import com.stockalert.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    @Query("SELECT p FROM Product p WHERE "
            + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE "
            + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND LOWER(p.category) = LOWER(:category)")
    Page<Product> searchByCategory(@Param("keyword") String keyword,
                                   @Param("category") String category,
                                   Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.quantity <= p.reorderLevel")
    Page<Product> findLowStock(Pageable pageable);
}
