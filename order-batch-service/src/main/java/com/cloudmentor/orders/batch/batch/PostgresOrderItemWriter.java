package com.cloudmentor.orders.batch.batch;

import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import com.cloudmentor.orders.batch.repository.ProcessedOrderRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Spring Batch ItemWriter that persists processed financial orders in PostgreSQL.
 * Implements idempotent upsert semantics by checking orderId uniqueness within the chunk transaction.
 */
@Component
public class PostgresOrderItemWriter implements ItemWriter<ProcessedOrderEntity> {

    private static final Logger log = LoggerFactory.getLogger(PostgresOrderItemWriter.class);

    private final ProcessedOrderRepository repository;

    public PostgresOrderItemWriter(ProcessedOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(Chunk<? extends ProcessedOrderEntity> chunk) {
        for (ProcessedOrderEntity incoming : chunk.getItems()) {
            Optional<ProcessedOrderEntity> existingOpt = repository.findByOrderId(incoming.getOrderId());
            if (existingOpt.isPresent()) {
                ProcessedOrderEntity existing = existingOpt.get();
                existing.setCustomerId(incoming.getCustomerId());
                existing.setTicker(incoming.getTicker());
                existing.setSide(incoming.getSide());
                existing.setQuantity(incoming.getQuantity());
                existing.setUnitPrice(incoming.getUnitPrice());
                existing.setCurrency(incoming.getCurrency());
                existing.setGrossAmount(incoming.getGrossAmount());
                existing.setCommissionFee(incoming.getCommissionFee());
                existing.setTaxAmount(incoming.getTaxAmount());
                existing.setNetSettlementAmount(incoming.getNetSettlementAmount());
                existing.setStatus(incoming.getStatus());
                existing.setSourceS3Key(incoming.getSourceS3Key());
                existing.setProcessedAt(incoming.getProcessedAt());
                repository.save(existing);
            } else {
                repository.save(incoming);
            }
        }
        log.info("Committed chunk of {} settled orders into PostgreSQL", chunk.size());
    }
}
