package com.cloudmentor.orders.processor.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Java 21 Record representing the output of the real-time business rule evaluation
 * performed when a new order file lands in Amazon S3.
 */
public record OrderRiskEvaluation(
        String orderId,
        String customerId,
        String ticker,
        BigDecimal notionalAmountUsd,
        RiskTier riskTier,
        boolean approvedForNightlyBatch,
        boolean requiresComplianceAlert,
        List<String> ruleFindings
) {
    public enum RiskTier {
        LOW,
        MEDIUM,
        HIGH_AML_REVIEW,
        REJECTED
    }
}
