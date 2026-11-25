package com.cloudmentor.orders.api.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Functional validator using Java 21 Records, Streams, and Optional.
 * Avoids heavy Hibernate Validator reflection in AWS Lambda to keep Cold Starts under 150ms.
 */
public record OrderValidationResult(
        boolean valid,
        List<String> errors,
        OrderRequest normalizedOrder
) {
    private static final Set<String> ALLOWED_SIDES = Set.of("BUY", "SELL");
    private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "MXN", "EUR");
    private static final int MAX_SINGLE_ORDER_QUANTITY = 100_000;

    public static OrderValidationResult validate(OrderRequest rawRequest) {
        if (rawRequest == null) {
            return new OrderValidationResult(false, List.of("Request body cannot be null or empty"), null);
        }

        OrderRequest normalized = rawRequest.withDefaultsIfMissing();

        List<Function<OrderRequest, Optional<String>>> rules = List.of(
                req -> req.customerId().isBlank()
                        ? Optional.of("customerId is required")
                        : Optional.empty(),
                req -> req.ticker().isBlank() || req.ticker().length() > 12
                        ? Optional.of("ticker is required and must be between 1 and 12 characters")
                        : Optional.empty(),
                req -> !ALLOWED_SIDES.contains(req.side())
                        ? Optional.of("side must be one of: " + ALLOWED_SIDES)
                        : Optional.empty(),
                req -> req.quantity() == null || req.quantity() <= 0 || req.quantity() > MAX_SINGLE_ORDER_QUANTITY
                        ? Optional.of("quantity must be between 1 and " + MAX_SINGLE_ORDER_QUANTITY)
                        : Optional.empty(),
                req -> req.unitPrice() == null || req.unitPrice().compareTo(BigDecimal.ZERO) <= 0
                        ? Optional.of("unitPrice must be greater than 0.00")
                        : Optional.empty(),
                req -> !ALLOWED_CURRENCIES.contains(req.currency())
                        ? Optional.of("currency must be one of: " + ALLOWED_CURRENCIES)
                        : Optional.empty()
        );

        List<String> violations = rules.stream()
                .map(rule -> rule.apply(normalized))
                .flatMap(Optional::stream)
                .toList();

        return new OrderValidationResult(violations.isEmpty(), violations, normalized);
    }
}
