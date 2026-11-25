package com.cloudmentor.orders.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cloudmentor.orders.batch.batch.FinancialOrderItemProcessor;
import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import com.cloudmentor.orders.batch.domain.OrderStatus;
import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import com.cloudmentor.orders.batch.dto.OrderCsvRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinancialOrderItemProcessorTest {

    private FinancialOrderItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new FinancialOrderItemProcessor();
    }

    @Test
    @DisplayName("Should calculate standard 0.25% commission + 16% IVA and add to gross amount on BUY order")
    void shouldCalculateStandardBuyOrderSettlement() {
        // Gross: 10 * 200.00 = 2000.00 (< 10,000 -> 0.25% commission = 5.0000; IVA 16% of 5.00 = 0.8000; Net = 2005.8000)
        OrderCsvRow row = new OrderCsvRow(
                "ORD-101", "CUST-01", "AAPL", "BUY", 10, new BigDecimal("200.00"), "USD", "incoming/csv/test.csv", 2
        );

        ProcessedOrderEntity entity = processor.process(row);

        assertEquals(new BigDecimal("2000.0000"), entity.getGrossAmount());
        assertEquals(new BigDecimal("5.0000"), entity.getCommissionFee());
        assertEquals(new BigDecimal("0.8000"), entity.getTaxAmount());
        assertEquals(new BigDecimal("2005.8000"), entity.getNetSettlementAmount());
        assertEquals(OrderStatus.SETTLED, entity.getStatus());
    }

    @Test
    @DisplayName("Should apply institutional 0.15% commission and subtract fees on SELL order >= 10,000")
    void shouldCalculateInstitutionalSellOrderSettlement() {
        // Gross: 100 * 200.00 = 20000.00 (>= 10,000 -> 0.15% commission = 30.0000; IVA 16% = 4.8000; Net SELL = 19965.2000)
        OrderCsvRow row = new OrderCsvRow(
                "ORD-102", "CUST-02", "MSFT", "SELL", 100, new BigDecimal("200.00"), "USD", "incoming/csv/test.csv", 3
        );

        ProcessedOrderEntity entity = processor.process(row);

        assertEquals(new BigDecimal("20000.0000"), entity.getGrossAmount());
        assertEquals(new BigDecimal("30.0000"), entity.getCommissionFee());
        assertEquals(new BigDecimal("4.8000"), entity.getTaxAmount());
        assertEquals(new BigDecimal("19965.2000"), entity.getNetSettlementAmount());
    }

    @Test
    @DisplayName("Should throw skippable InvalidOrderRecordException when quantity is negative")
    void shouldThrowSkippableExceptionOnNegativeQuantity() {
        OrderCsvRow invalidRow = new OrderCsvRow(
                "ORD-BAD", "CUST-88", "NVDA", "BUY", -5, new BigDecimal("120.00"), "USD", "incoming/csv/test.csv", 4
        );

        assertThrows(InvalidOrderRecordException.class, () -> processor.process(invalidRow));
    }
}
