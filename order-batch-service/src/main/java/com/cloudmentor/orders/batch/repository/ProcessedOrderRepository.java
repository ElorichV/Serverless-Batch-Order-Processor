package com.cloudmentor.orders.batch.repository;

import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrderEntity, Long> {

    Optional<ProcessedOrderEntity> findByOrderId(String orderId);

    List<ProcessedOrderEntity> findTop100ByOrderByProcessedAtDesc();
}
