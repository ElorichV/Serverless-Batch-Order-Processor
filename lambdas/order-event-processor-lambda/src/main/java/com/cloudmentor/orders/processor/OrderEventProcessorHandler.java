package com.cloudmentor.orders.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cloudmentor.orders.processor.model.OrderRiskEvaluation;
import com.cloudmentor.orders.processor.model.S3EventDetail;
import com.cloudmentor.orders.processor.model.S3EventDetail.OrderPayload;
import com.cloudmentor.orders.processor.service.OrderBusinessRuleEngine;
import com.cloudmentor.orders.processor.util.StructuredJsonLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.crac.Core;
import org.crac.Resource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * AWS Lambda handler triggered automatically by Amazon EventBridge when a file lands in S3.
 *
 * Flow:
 * 1. Parses EventBridge S3 Object Created event.
 * 2. Downloads the newly created JSON or CSV object from S3.
 * 3. Evaluates business rules (AML thresholds, FX conversion, block trade limits) via OrderBusinessRuleEngine.
 * 4. Emits structured JSON logs to CloudWatch Logs (indexed by CloudWatch Metric Filters).
 */
public class OrderEventProcessorHandler implements RequestHandler<Map<String, Object>, Map<String, Object>>, Resource {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final StructuredJsonLogger LOGGER = new StructuredJsonLogger("order-event-processor-lambda");

    private final S3Client s3Client;
    private final OrderBusinessRuleEngine ruleEngine;

    public OrderEventProcessorHandler() {
        this(buildDefaultS3Client(), new OrderBusinessRuleEngine());
    }

    public OrderEventProcessorHandler(S3Client s3Client, OrderBusinessRuleEngine ruleEngine) {
        this.s3Client = s3Client;
        this.ruleEngine = ruleEngine;
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) {
        OrderPayload warmup = new OrderPayload("ORD-WARM", "CUST-01", "MSFT", "BUY", 10, new BigDecimal("400.00"), "USD");
        ruleEngine.evaluate(warmup);
        LOGGER.info("SNAPSTART_BEFORE_CHECKPOINT", "warmup", "Pre-warmed OrderBusinessRuleEngine for SnapStart", Map.of());
    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) {
        LOGGER.info("SNAPSTART_AFTER_RESTORE", "restore", "Restored order-event-processor-lambda from SnapStart", Map.of());
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        String correlationId = Optional.ofNullable(context)
                .map(Context::getAwsRequestId)
                .orElse("local-evt");

        Optional<S3EventDetail> maybeDetail = S3EventDetail.fromEventBridgePayload(event);
        if (maybeDetail.isEmpty()) {
            LOGGER.warn("UNRECOGNIZED_EVENT_SCHEMA", correlationId, "Received event without S3 bucket/object detail", Map.of(
                    "rawKeys", event != null ? event.keySet() : "null"
            ));
            return Map.of("status", "IGNORED", "reason", "Not an S3 Object Created event");
        }

        S3EventDetail s3Detail = maybeDetail.get();
        String decodedKey = URLDecoder.decode(s3Detail.objectKey(), StandardCharsets.UTF_8);

        LOGGER.info("S3_EVENT_RECEIVED", correlationId, "Processing S3 object via EventBridge trigger", Map.of(
                "eventId", s3Detail.eventId(),
                "bucket", s3Detail.bucketName(),
                "key", decodedKey,
                "sizeBytes", s3Detail.objectSize()
        ));

        try {
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(s3Detail.bucketName())
                    .key(decodedKey)
                    .build());

            String content = objectBytes.asUtf8String();
            Optional<OrderPayload> parsedOrder = parseOrderContent(decodedKey, content);

            if (parsedOrder.isEmpty()) {
                LOGGER.warn("ORDER_PARSE_SKIPPED", correlationId, "Could not extract single order payload from S3 object", Map.of(
                        "bucket", s3Detail.bucketName(),
                        "key", decodedKey
                ));
                return Map.of("status", "SKIPPED_NON_ORDER_OBJECT", "key", decodedKey);
            }

            OrderRiskEvaluation evaluation = ruleEngine.evaluate(parsedOrder.get());

            Map<String, Object> auditMetadata = new LinkedHashMap<>();
            auditMetadata.put("orderId", evaluation.orderId());
            auditMetadata.put("customerId", evaluation.customerId());
            auditMetadata.put("ticker", evaluation.ticker());
            auditMetadata.put("notionalAmountUsd", evaluation.notionalAmountUsd());
            auditMetadata.put("riskTier", evaluation.riskTier().name());
            auditMetadata.put("approvedForNightlyBatch", evaluation.approvedForNightlyBatch());
            auditMetadata.put("requiresComplianceAlert", evaluation.requiresComplianceAlert());
            auditMetadata.put("ruleFindings", evaluation.ruleFindings());
            auditMetadata.put("sourceBucket", s3Detail.bucketName());
            auditMetadata.put("sourceKey", decodedKey);

            if (evaluation.requiresComplianceAlert()) {
                LOGGER.warn("ORDER_COMPLIANCE_ALERT", correlationId,
                        "High-value or flagged financial order detected by business rules", auditMetadata);
            } else {
                LOGGER.info("ORDER_EVENT_VALIDATED", correlationId,
                        "Order passed real-time EventBridge validation and is queued for Spring Batch settlement", auditMetadata);
            }

            return Map.of(
                    "status", "PROCESSED",
                    "orderId", evaluation.orderId(),
                    "riskTier", evaluation.riskTier().name(),
                    "approvedForNightlyBatch", evaluation.approvedForNightlyBatch(),
                    "notionalAmountUsd", evaluation.notionalAmountUsd()
            );

        } catch (Exception ex) {
            LOGGER.error("EVENT_PROCESSING_FAILED", correlationId, "Failed to process S3 event from EventBridge", Map.of(
                    "bucket", s3Detail.bucketName(),
                    "key", decodedKey
            ), ex);
            throw new IllegalStateException("Failed to process S3 order event for key: " + decodedKey, ex);
        }
    }

    private Optional<OrderPayload> parseOrderContent(String key, String content) throws Exception {
        if (key.endsWith(".csv")) {
            return OrderBusinessRuleEngine.parseFromCsvContent(content);
        }
        JsonNode root = OBJECT_MAPPER.readTree(content);
        JsonNode orderNode = root.has("order") ? root.get("order") : root;
        if (orderNode == null || !orderNode.has("orderId")) {
            return Optional.empty();
        }
        return Optional.of(new OrderPayload(
                orderNode.path("orderId").asText(),
                orderNode.path("customerId").asText(),
                orderNode.path("ticker").asText(),
                orderNode.path("side").asText(),
                orderNode.path("quantity").asInt(0),
                new BigDecimal(orderNode.path("unitPrice").asText("0.00")),
                orderNode.path("currency").asText("USD")
        ));
    }

    private static S3Client buildDefaultS3Client() {
        String regionStr = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("us-east-1");
        String endpointOverride = Optional.ofNullable(System.getenv("AWS_ENDPOINT_URL"))
                .or(() -> Optional.ofNullable(System.getenv("LOCALSTACK_HOSTNAME"))
                        .map(host -> "http://" + host + ":4566"))
                .orElse(null);

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(regionStr))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")
                    ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
