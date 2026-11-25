package com.cloudmentor.orders.batch.dto;

import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read-only projection DTO (Java 21 Record) for API consumers.
 * Prevents direct exposure of JPA entities.
 */
public record ProcessedOrderDto(
        Long id,
        String orderId,
        String customerId,
        String ticker,
        String side,
        Integer quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal grossAmount,
        BigDecimal commissionFee,
        BigDecimal taxAmount,
        BigDecimal netSettlementAmount,
        String status,
        String sourceS3Key,
        OffsetDateTime processedAt
) {
    public static ProcessedOrderDto fromEntity(ProcessedOrderEntity entity) {
        return new ProcessedOrderDto(
                entity.getId(),
                entity.getOrderId(),
                entity.getCustomerId(),
                entity.getTicker(),
                entity.getSide(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getCurrency(),
                entity.getGrossAmount(),
                entity.getCommissionFee(),
                entity.getTaxAmount(),
                entity.getNetSettlementAmount(),
                entity.getStatus().name(),
                entity.getSourceS3Key(),
                entity.getProcessedAt()
        );
    }
}
