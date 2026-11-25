package com.cloudmentor.orders.batch.batch;

import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import com.cloudmentor.orders.batch.domain.OrderStatus;
import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import com.cloudmentor.orders.batch.dto.OrderCsvRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Spring Batch ItemProcessor that validates financial rules and calculates:
 * 1. Gross Notional Amount = quantity * unitPrice
 * 2. Tiered Brokerage Commission:
 *    - Standard (< 10,000 gross): 0.25% (0.0025)
 *    - Institutional (>= 10,000 gross): 0.15% (0.0015)
 * 3. Tax (16% VAT/IVA applied over the brokerage commission)
 * 4. Net Settlement Amount:
 *    - BUY order: grossAmount + commissionFee + taxAmount
 *    - SELL order: grossAmount - commissionFee - taxAmount
 */
@Component
public class FinancialOrderItemProcessor implements ItemProcessor<OrderCsvRow, ProcessedOrderEntity> {

    private static final Set<String> VALID_SIDES = Set.of("BUY", "SELL");
    private static final Set<String> VALID_CURRENCIES = Set.of("USD", "MXN", "EUR");

    private static final BigDecimal INSTITUTIONAL_TIER_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");
    private static final BigDecimal STANDARD_COMMISSION_RATE = new BigDecimal("0.0025");
    private static final BigDecimal INSTITUTIONAL_COMMISSION_RATE = new BigDecimal("0.0015");
    private static final BigDecimal TAX_IVA_RATE = new BigDecimal("0.1600");

    @Override
    public ProcessedOrderEntity process(OrderCsvRow item) {
        validateRow(item);

        String normalizedSide = item.side().toUpperCase(Locale.ROOT);
        String normalizedCurrency = item.currency().toUpperCase(Locale.ROOT);
        String normalizedTicker = item.ticker().toUpperCase(Locale.ROOT);

        BigDecimal grossAmount = item.unitPrice()
                .multiply(BigDecimal.valueOf(item.quantity()))
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal commissionRate = grossAmount.compareTo(INSTITUTIONAL_TIER_THRESHOLD) >= 0
                ? INSTITUTIONAL_COMMISSION_RATE
                : STANDARD_COMMISSION_RATE;

        BigDecimal commissionFee = grossAmount
                .multiply(commissionRate)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal taxAmount = commissionFee
                .multiply(TAX_IVA_RATE)
                .setScale(4, RoundingMode.HALF_UP);

        BigDecimal netSettlementAmount = "BUY".equals(normalizedSide)
                ? grossAmount.add(commissionFee).add(taxAmount).setScale(4, RoundingMode.HALF_UP)
                : grossAmount.subtract(commissionFee).subtract(taxAmount).setScale(4, RoundingMode.HALF_UP);

        OrderStatus status = grossAmount.compareTo(HIGH_VALUE_THRESHOLD) >= 0
                ? OrderStatus.SETTLED_HIGH_VALUE
                : OrderStatus.SETTLED;

        return new ProcessedOrderEntity(
                item.orderId(),
                item.customerId(),
                normalizedTicker,
                normalizedSide,
                item.quantity(),
                item.unitPrice().setScale(4, RoundingMode.HALF_UP),
                normalizedCurrency,
                grossAmount,
                commissionFee,
                taxAmount,
                netSettlementAmount,
                status,
                item.sourceS3Key(),
                null,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void validateRow(OrderCsvRow item) {
        if (item.orderId() == null || item.orderId().isBlank()) {
            throw new InvalidOrderRecordException("UNKNOWN", item.sourceS3Key(), "Missing orderId");
        }
        if (item.customerId() == null || item.customerId().isBlank()) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "Missing customerId");
        }
        if (item.ticker() == null || item.ticker().isBlank() || "INVALID_ROW".equalsIgnoreCase(item.ticker())) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "Invalid ticker symbol: " + item.ticker());
        }
        if (item.side() == null || !VALID_SIDES.contains(item.side().toUpperCase(Locale.ROOT))) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "Unsupported side: " + item.side());
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "Quantity must be positive, got: " + item.quantity());
        }
        if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "UnitPrice must be positive, got: " + item.unitPrice());
        }
        if (item.currency() == null || !VALID_CURRENCIES.contains(item.currency().toUpperCase(Locale.ROOT))) {
            throw new InvalidOrderRecordException(item.orderId(), item.sourceS3Key(), "Unsupported currency: " + item.currency());
        }
    }
}
