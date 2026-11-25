package com.cloudmentor.orders.batch.batch;

import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import com.cloudmentor.orders.batch.dto.OrderCsvRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

/**
 * Audit listener that records skipped CSV lines without failing the entire chunk transaction.
 */
@Component
public class OrderSkipListener implements SkipListener<OrderCsvRow, ProcessedOrderEntity> {

    private static final Logger log = LoggerFactory.getLogger(OrderSkipListener.class);

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof InvalidOrderRecordException ex) {
            log.warn("SKIP_IN_READ orderId={} s3Key={} reason={}", ex.getOrderId(), ex.getSourceS3Key(), ex.getMessage());
        } else {
            log.warn("SKIP_IN_READ unexpected error={}", t.getMessage());
        }
    }

    @Override
    public void onSkipInProcess(OrderCsvRow item, Throwable t) {
        log.warn("SKIP_IN_PROCESS orderId={} s3Key={} line={} reason={}",
                item.orderId(), item.sourceS3Key(), item.lineNumber(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(ProcessedOrderEntity item, Throwable t) {
        log.error("SKIP_IN_WRITE orderId={} s3Key={} reason={}",
                item.getOrderId(), item.getSourceS3Key(), t.getMessage());
    }
}
