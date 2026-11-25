package com.cloudmentor.orders.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JPA Entity mapped to PostgreSQL table processed_orders.
 *
 * Mentor Note:
 * 1. Never expose this entity directly in REST Controllers; always map to ProcessedOrderDto (Record).
 * 2. Uses OffsetDateTime mapped to PostgreSQL TIMESTAMPTZ and BigDecimal(18,4) for financial accuracy.
 */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "ticker", nullable = false, length = 16)
    private String ticker;

    @Column(name = "side", nullable = false, length = 8)
    private String side;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "commission_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal commissionFee;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "net_settlement_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netSettlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "source_s3_key", nullable = false, length = 255)
    private String sourceS3Key;

    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    public ProcessedOrderEntity() {
    }

    public ProcessedOrderEntity(
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
            OrderStatus status,
            String sourceS3Key,
            Long jobExecutionId,
            OffsetDateTime processedAt
    ) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.grossAmount = grossAmount;
        this.commissionFee = commissionFee;
        this.taxAmount = taxAmount;
        this.netSettlementAmount = netSettlementAmount;
        this.status = status;
        this.sourceS3Key = sourceS3Key;
        this.jobExecutionId = jobExecutionId;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getCommissionFee() {
        return commissionFee;
    }

    public void setCommissionFee(BigDecimal commissionFee) {
        this.commissionFee = commissionFee;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getNetSettlementAmount() {
        return netSettlementAmount;
    }

    public void setNetSettlementAmount(BigDecimal netSettlementAmount) {
        this.netSettlementAmount = netSettlementAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getSourceS3Key() {
        return sourceS3Key;
    }

    public void setSourceS3Key(String sourceS3Key) {
        this.sourceS3Key = sourceS3Key;
    }

    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    public void setJobExecutionId(Long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
