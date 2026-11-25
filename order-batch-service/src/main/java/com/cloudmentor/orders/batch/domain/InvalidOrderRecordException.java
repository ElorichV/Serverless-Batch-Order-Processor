package com.cloudmentor.orders.batch.domain;

/**
 * Domain exception thrown by the Spring Batch ItemProcessor when a CSV record violates
 * financial invariants. Configured as a skippable exception in the FaultTolerant Step
 * so a single corrupted CSV row does not abort a 10,000-row batch settlement run.
 */
public class InvalidOrderRecordException extends RuntimeException {

    private final String orderId;
    private final String sourceS3Key;

    public InvalidOrderRecordException(String orderId, String sourceS3Key, String message) {
        super(message);
        this.orderId = orderId;
        this.sourceS3Key = sourceS3Key;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSourceS3Key() {
        return sourceS3Key;
    }
}
