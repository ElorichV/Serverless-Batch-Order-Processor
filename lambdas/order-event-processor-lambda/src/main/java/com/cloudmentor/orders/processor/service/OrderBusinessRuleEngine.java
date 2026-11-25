package com.cloudmentor.orders.processor.service;

import com.cloudmentor.orders.processor.model.OrderRiskEvaluation;
import com.cloudmentor.orders.processor.model.OrderRiskEvaluation.RiskTier;
import com.cloudmentor.orders.processor.model.S3EventDetail.OrderPayload;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Domain service that evaluates financial compliance & risk rules in real time
 * when EventBridge notifies that an order arrived in S3.
 *
 * Uses Java 21 switch expressions, Records, Streams, and Optional.
 */
public class OrderBusinessRuleEngine {

    private static final BigDecimal AML_REVIEW_THRESHOLD_USD = new BigDecimal("50000.00");
    private static final BigDecimal MEDIUM_RISK_THRESHOLD_USD = new BigDecimal("10000.00");
    private static final BigDecimal MXN_TO_USD_RATE = new BigDecimal("0.058");
    private static final BigDecimal EUR_TO_USD_RATE = new BigDecimal("1.08");

    public OrderRiskEvaluation evaluate(OrderPayload order) {
        List<String> findings = new ArrayList<>();

        if (order.quantity() <= 0 || order.unitPrice() == null || order.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            findings.add("INVALID_FINANCIAL_VALUES: Quantity and unitPrice must be strictly positive");
            return new OrderRiskEvaluation(
                    order.orderId(),
                    order.customerId(),
                    order.ticker(),
                    BigDecimal.ZERO,
                    RiskTier.REJECTED,
                    false,
                    true,
                    List.copyOf(findings)
            );
        }

        BigDecimal fxRateToUsd = switch (Optional.ofNullable(order.currency()).orElse("USD").toUpperCase(Locale.ROOT)) {
            case "USD" -> BigDecimal.ONE;
            case "MXN" -> MXN_TO_USD_RATE;
            case "EUR" -> EUR_TO_USD_RATE;
            default -> {
                findings.add("UNSUPPORTED_CURRENCY: " + order.currency());
                yield BigDecimal.ZERO;
            }
        };

        if (fxRateToUsd.compareTo(BigDecimal.ZERO) == 0) {
            return new OrderRiskEvaluation(
                    order.orderId(),
                    order.customerId(),
                    order.ticker(),
                    BigDecimal.ZERO,
                    RiskTier.REJECTED,
                    false,
                    true,
                    List.copyOf(findings)
            );
        }

        BigDecimal notionalUsd = order.notionalAmount()
                .multiply(fxRateToUsd)
                .setScale(2, RoundingMode.HALF_UP);

        Stream.<Optional<String>>of(
                notionalUsd.compareTo(AML_REVIEW_THRESHOLD_USD) >= 0
                        ? Optional.of("AML_THRESHOLD_EXCEEDED: Notional USD >= 50,000 requires compliance audit log")
                        : Optional.empty(),
                order.quantity() >= 5_000
                        ? Optional.of("BLOCK_TRADE_VOLUME: High share volume >= 5,000 units")
                        : Optional.empty(),
                order.unitPrice().compareTo(new BigDecimal("1.00")) < 0
                        ? Optional.of("PENNY_STOCK_VOLATILITY: Unit price below 1.00")
                        : Optional.empty()
        ).flatMap(Optional::stream).forEach(findings::add);

        RiskTier tier;
        boolean requiresAlert = false;

        if (notionalUsd.compareTo(AML_REVIEW_THRESHOLD_USD) >= 0) {
            tier = RiskTier.HIGH_AML_REVIEW;
            requiresAlert = true;
        } else if (notionalUsd.compareTo(MEDIUM_RISK_THRESHOLD_USD) >= 0 || !findings.isEmpty()) {
            tier = RiskTier.MEDIUM;
        } else {
            tier = RiskTier.LOW;
            findings.add("STANDARD_SETTLEMENT_APPROVED");
        }

        return new OrderRiskEvaluation(
                order.orderId(),
                order.customerId(),
                order.ticker(),
                notionalUsd,
                tier,
                true,
                requiresAlert,
                List.copyOf(findings)
        );
    }

    public static Optional<OrderPayload> parseFromCsvContent(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return Optional.empty();
        }
        return csvContent.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("orderId,"))
                .findFirst()
                .flatMap(line -> {
                    String[] cols = line.split(",");
                    if (cols.length < 7) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.of(new OrderPayload(
                                cols[0].trim(),
                                cols[1].trim(),
                                cols[2].trim(),
                                cols[3].trim(),
                                Integer.parseInt(cols[4].trim()),
                                new BigDecimal(cols[5].trim()),
                                cols[6].trim()
                        ));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
    }
}
