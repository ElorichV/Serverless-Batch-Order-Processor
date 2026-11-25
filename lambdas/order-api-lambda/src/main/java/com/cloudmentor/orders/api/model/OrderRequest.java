package com.cloudmentor.orders.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Inmutable DTO (Java 21 Record) representing an incoming financial order payload.
 * Mentor Note: Using Java Records eliminates boilerplate and works seamlessly with Jackson
 * without needing reflection-heavy setters, improving startup performance on AWS Lambda.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRequest(
        String orderId,
        String customerId,
        String ticker,
        String side,
        Integer quantity,
        BigDecimal unitPrice,
        String currency
) {

    public OrderRequest withDefaultsIfMissing() {
        String resolvedOrderId = Optional.ofNullable(orderId)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .orElseGet(() -> "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));

        String normalizedTicker = Optional.ofNullable(ticker)
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .orElse("");

        String normalizedSide = Optional.ofNullable(side)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .orElse("");

        String normalizedCurrency = Optional.ofNullable(currency)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .orElse("USD");

        return new OrderRequest(
                resolvedOrderId,
                Optional.ofNullable(customerId).map(String::trim).orElse(""),
                normalizedTicker,
                normalizedSide,
                quantity,
                unitPrice != null ? unitPrice.setScale(2, RoundingMode.HALF_UP) : null,
                normalizedCurrency
        );
    }

    public BigDecimal notionalAmount() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    public String toCsvWithHeader() {
        return "orderId,customerId,ticker,side,quantity,unitPrice,currency\n" + toCsvRow();
    }

    public String toCsvRow() {
        return String.join(",",
                escapeCsv(orderId),
                escapeCsv(customerId),
                escapeCsv(ticker),
                escapeCsv(side),
                String.valueOf(quantity != null ? quantity : 0),
                unitPrice != null ? unitPrice.toPlainString() : "0.00",
                escapeCsv(currency)
        );
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(",", "").trim();
    }
}
