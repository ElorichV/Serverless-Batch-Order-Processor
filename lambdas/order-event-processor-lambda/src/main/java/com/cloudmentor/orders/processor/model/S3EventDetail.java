package com.cloudmentor.orders.processor.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable Java 21 Record representing an extracted S3 Object Created event from Amazon EventBridge.
 */
public record S3EventDetail(
        String eventId,
        String eventSource,
        String bucketName,
        String objectKey,
        long objectSize
) {

    @SuppressWarnings("unchecked")
    public static Optional<S3EventDetail> fromEventBridgePayload(Map<String, Object> rawEvent) {
        if (rawEvent == null || rawEvent.isEmpty()) {
            return Optional.empty();
        }

        // Standard EventBridge S3 Object Created schema: { "id": "...", "source": "aws.s3", "detail": { "bucket": { "name": "..." }, "object": { "key": "...", "size": 123 } } }
        Object detailObj = rawEvent.get("detail");
        if (detailObj instanceof Map<?, ?> detailMap) {
            Map<String, Object> detail = (Map<String, Object>) detailMap;
            Map<String, Object> bucket = (Map<String, Object>) detail.get("bucket");
            Map<String, Object> object = (Map<String, Object>) detail.get("object");

            String bucketName = Optional.ofNullable(bucket)
                    .map(b -> String.valueOf(b.get("name")))
                    .orElse("");
            String objectKey = Optional.ofNullable(object)
                    .map(o -> String.valueOf(o.get("key")))
                    .orElse("");
            long size = Optional.ofNullable(object)
                    .map(o -> o.get("size"))
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::longValue)
                    .orElse(0L);

            if (!bucketName.isBlank() && !objectKey.isBlank()) {
                return Optional.of(new S3EventDetail(
                        String.valueOf(rawEvent.getOrDefault("id", "evt-unknown")),
                        String.valueOf(rawEvent.getOrDefault("source", "aws.s3")),
                        bucketName,
                        objectKey,
                        size
                ));
            }
        }

        // Fallback for direct S3 Records event format (Records[0].s3.bucket.name)
        Object recordsObj = rawEvent.get("Records");
        if (recordsObj instanceof List<?> records && !records.isEmpty() && records.getFirst() instanceof Map<?, ?> firstRec) {
            Map<String, Object> recordMap = (Map<String, Object>) firstRec;
            Map<String, Object> s3Map = (Map<String, Object>) recordMap.get("s3");
            if (s3Map != null) {
                Map<String, Object> bucket = (Map<String, Object>) s3Map.get("bucket");
                Map<String, Object> object = (Map<String, Object>) s3Map.get("object");
                String bucketName = bucket != null ? String.valueOf(bucket.get("name")) : "";
                String objectKey = object != null ? String.valueOf(object.get("key")) : "";
                long size = object != null && object.get("size") instanceof Number n ? n.longValue() : 0L;
                if (!bucketName.isBlank() && !objectKey.isBlank()) {
                    return Optional.of(new S3EventDetail("evt-s3-direct", "aws:s3", bucketName, objectKey, size));
                }
            }
        }

        return Optional.empty();
    }

    public record OrderPayload(
            String orderId,
            String customerId,
            String ticker,
            String side,
            int quantity,
            BigDecimal unitPrice,
            String currency
    ) {
        public BigDecimal notionalAmount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
