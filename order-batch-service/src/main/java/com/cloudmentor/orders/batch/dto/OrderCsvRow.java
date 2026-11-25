package com.cloudmentor.orders.batch.dto;

import java.math.BigDecimal;

/**
 * Java 21 Record representing a raw CSV line read from Amazon S3 by the Spring Batch ItemReader.
 */
public record OrderCsvRow(
        String orderId,
        String customerId,
        String ticker,
        String side,
        Integer quantity,
        BigDecimal unitPrice,
        String currency,
        String sourceS3Key,
        int lineNumber
) {}
