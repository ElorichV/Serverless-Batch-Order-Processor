package com.cloudmentor.orders.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.cloudmentor.orders.api.model.OrderRequest;
import com.cloudmentor.orders.api.model.OrderValidationResult;
import com.cloudmentor.orders.api.service.S3OrderStorageService;
import com.cloudmentor.orders.api.service.S3OrderStorageService.StoredOrderArtifacts;
import com.cloudmentor.orders.api.util.StructuredJsonLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.crac.Core;
import org.crac.Resource;

/**
 * AWS Lambda handler exposed via Amazon API Gateway (POST /orders).
 *
 * Mentor Architecture Highlights:
 * 1. Implements org.crac.Resource to support AWS Lambda SnapStart. During the snapshot creation phase
 *    (beforeCheckpoint), we pre-warm Jackson serialization and validator pipelines so restored microVMs
 *    respond in single-digit milliseconds without JIT cold-start penalty.
 * 2. Uses static initialization for ObjectMapper, S3OrderStorageService, and StructuredJsonLogger
 *    so execution environments reuse connections across warm invocations.
 */
public class OrderApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>, Resource {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final StructuredJsonLogger LOGGER = new StructuredJsonLogger("order-api-lambda");

    private final S3OrderStorageService storageService;

    public OrderApiHandler() {
        this(new S3OrderStorageService());
    }

    public OrderApiHandler(S3OrderStorageService storageService) {
        this.storageService = storageService;
        Core.getGlobalContext().register(this);
    }

    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) throws Exception {
        // Pre-warm Jackson and functional validation before Firecracker microVM snapshot is taken
        OrderRequest warmupOrder = new OrderRequest("ORD-WARMUP", "CUST-000", "AAPL", "BUY", 10, new BigDecimal("150.00"), "USD");
        OrderValidationResult.validate(warmupOrder);
        OBJECT_MAPPER.writeValueAsString(warmupOrder);
        LOGGER.info("SNAPSTART_BEFORE_CHECKPOINT", "warmup", "JVM classes and Jackson pre-warmed for SnapStart", Map.of());
    }

    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) throws Exception {
        LOGGER.info("SNAPSTART_AFTER_RESTORE", "restore", "MicroVM restored from SnapStart snapshot", Map.of());
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String correlationId = resolveCorrelationId(request, context);

        try {
            String rawBody = Optional.ofNullable(request)
                    .map(APIGatewayProxyRequestEvent::getBody)
                    .map(String::trim)
                    .filter(body -> !body.isEmpty())
                    .orElse(null);

            if (rawBody == null) {
                LOGGER.warn("ORDER_EMPTY_BODY", correlationId, "Rejected request with empty payload", Map.of());
                return buildJsonResponse(400, correlationId, Map.of(
                        "status", "BAD_REQUEST",
                        "correlationId", correlationId,
                        "message", "Request body is required"
                ));
            }

            OrderRequest parsedRequest = OBJECT_MAPPER.readValue(rawBody, OrderRequest.class);
            OrderValidationResult validation = OrderValidationResult.validate(parsedRequest);

            if (!validation.valid()) {
                LOGGER.warn("ORDER_VALIDATION_FAILED", correlationId, "Order payload failed domain validation", Map.of(
                        "errors", validation.errors()
                ));
                return buildJsonResponse(400, correlationId, Map.of(
                        "status", "VALIDATION_ERROR",
                        "correlationId", correlationId,
                        "errors", validation.errors()
                ));
            }

            OrderRequest validOrder = validation.normalizedOrder();
            StoredOrderArtifacts artifacts = storageService.storeOrder(validOrder, correlationId);

            LOGGER.info("ORDER_INGESTED", correlationId, "Order validated and persisted to S3", Map.of(
                    "orderId", validOrder.orderId(),
                    "customerId", validOrder.customerId(),
                    "ticker", validOrder.ticker(),
                    "side", validOrder.side(),
                    "notionalAmount", validOrder.notionalAmount(),
                    "currency", validOrder.currency(),
                    "bucket", artifacts.bucket(),
                    "jsonKey", artifacts.jsonKey(),
                    "csvKey", artifacts.csvKey()
            ));

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("status", "ACCEPTED");
            responseBody.put("correlationId", correlationId);
            responseBody.put("orderId", validOrder.orderId());
            responseBody.put("notionalAmount", validOrder.notionalAmount());
            responseBody.put("currency", validOrder.currency());
            responseBody.put("s3Bucket", artifacts.bucket());
            responseBody.put("s3JsonKey", artifacts.jsonKey());
            responseBody.put("s3CsvKey", artifacts.csvKey());

            return buildJsonResponse(201, correlationId, responseBody);

        } catch (com.fasterxml.jackson.core.JsonProcessingException jsonEx) {
            LOGGER.warn("ORDER_MALFORMED_JSON", correlationId, "Invalid JSON format in request body", Map.of(), jsonEx);
            return buildJsonResponse(400, correlationId, Map.of(
                    "status", "MALFORMED_JSON",
                    "correlationId", correlationId,
                    "message", "Request payload is not valid JSON"
            ));
        } catch (Exception ex) {
            LOGGER.error("ORDER_INGESTION_ERROR", correlationId, "Unexpected error storing order in S3", Map.of(), ex);
            return buildJsonResponse(500, correlationId, Map.of(
                    "status", "INTERNAL_SERVER_ERROR",
                    "correlationId", correlationId,
                    "message", "Failed to process order ingestion"
            ));
        }
    }

    private String resolveCorrelationId(APIGatewayProxyRequestEvent request, Context context) {
        return Optional.ofNullable(request)
                .map(APIGatewayProxyRequestEvent::getHeaders)
                .flatMap(headers -> Optional.ofNullable(headers.get("X-Correlation-Id"))
                        .or(() -> Optional.ofNullable(headers.get("x-correlation-id"))))
                .or(() -> Optional.ofNullable(context).map(Context::getAwsRequestId))
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private APIGatewayProxyResponseEvent buildJsonResponse(int statusCode, String correlationId, Map<String, Object> bodyMap) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "X-Correlation-Id", correlationId
                    ))
                    .withBody(OBJECT_MAPPER.writeValueAsString(bodyMap));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"status\":\"INTERNAL_SERVER_ERROR\"}");
        }
    }
}
