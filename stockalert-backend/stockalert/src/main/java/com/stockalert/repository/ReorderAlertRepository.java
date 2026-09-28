package com.stockalert.repository;

import com.stockalert.model.ReorderAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReorderAlertRepository extends JpaRepository<ReorderAlert, Long> {

    Page<ReorderAlert> findByResolved(boolean resolved, Pageable pageable);

    List<ReorderAlert> findByProductIdAndResolvedFalse(Long productId);

    void deleteByProductId(Long productId);
}
