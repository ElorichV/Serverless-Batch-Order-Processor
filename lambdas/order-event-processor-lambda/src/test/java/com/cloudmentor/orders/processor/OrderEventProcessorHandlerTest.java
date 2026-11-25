package com.cloudmentor.orders.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.cloudmentor.orders.processor.service.OrderBusinessRuleEngine;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@ExtendWith(MockitoExtension.class)
class OrderEventProcessorHandlerTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private Context lambdaContext;

    private OrderEventProcessorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderEventProcessorHandler(s3Client, new OrderBusinessRuleEngine());
    }

    @Test
    @DisplayName("Should classify standard order as LOW risk when EventBridge triggers on S3 JSON creation")
    void shouldProcessStandardOrderFromEventBridge() {
        when(lambdaContext.getAwsRequestId()).thenReturn("evt-req-001");
        String s3Json = """
                {
                  "order": {
                    "orderId": "ORD-2026-100",
                    "customerId": "CUST-101",
                    "ticker": "AAPL",
                    "side": "BUY",
                    "quantity": 10,
                    "unitPrice": 190.00,
                    "currency": "USD"
                  }
                }
                """;

        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                s3Json.getBytes(StandardCharsets.UTF_8)
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        Map<String, Object> eventBridgeEvent = Map.of(
                "id", "eb-123",
                "source", "aws.s3",
                "detail-type", "Object Created",
                "detail", Map.of(
                        "bucket", Map.of("name", "financial-orders-bucket"),
                        "object", Map.of("key", "incoming/json/2026-11-24/ORD-2026-100.json", "size", 240)
                )
        );

        Map<String, Object> result = handler.handleRequest(eventBridgeEvent, lambdaContext);

        assertEquals("PROCESSED", result.get("status"));
        assertEquals("ORD-2026-100", result.get("orderId"));
        assertEquals("LOW", result.get("riskTier"));
        assertTrue((Boolean) result.get("approvedForNightlyBatch"));
    }

    @Test
    @DisplayName("Should flag high-value order (>= 50,000 USD) as HIGH_AML_REVIEW")
    void shouldFlagHighValueOrderForAmlReview() {
        when(lambdaContext.getAwsRequestId()).thenReturn("evt-req-002");
        String s3Csv = """
                orderId,customerId,ticker,side,quantity,unitPrice,currency
                ORD-2026-999,CUST-WHALE,NVDA,BUY,500,130.00,USD
                """;

        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                s3Csv.getBytes(StandardCharsets.UTF_8)
        );
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        Map<String, Object> eventBridgeEvent = Map.of(
                "id", "eb-456",
                "source", "aws.s3",
                "detail-type", "Object Created",
                "detail", Map.of(
                        "bucket", Map.of("name", "financial-orders-bucket"),
                        "object", Map.of("key", "incoming/csv/ORD-2026-999.csv", "size", 128)
                )
        );

        Map<String, Object> result = handler.handleRequest(eventBridgeEvent, lambdaContext);

        assertEquals("PROCESSED", result.get("status"));
        assertEquals("HIGH_AML_REVIEW", result.get("riskTier"));
    }
}
