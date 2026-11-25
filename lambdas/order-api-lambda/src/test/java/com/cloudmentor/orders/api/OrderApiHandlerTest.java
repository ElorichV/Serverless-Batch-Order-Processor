package com.cloudmentor.orders.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.cloudmentor.orders.api.model.OrderRequest;
import com.cloudmentor.orders.api.service.S3OrderStorageService;
import com.cloudmentor.orders.api.service.S3OrderStorageService.StoredOrderArtifacts;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApiHandlerTest {

    @Mock
    private S3OrderStorageService storageService;

    @Mock
    private Context lambdaContext;

    private OrderApiHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderApiHandler(storageService);
    }

    @Test
    @DisplayName("Should return 201 Created and store JSON + CSV in S3 when order payload is valid")
    void shouldAcceptValidOrderAndUploadToS3() throws Exception {
        when(lambdaContext.getAwsRequestId()).thenReturn("req-12345");
        when(storageService.storeOrder(any(OrderRequest.class), anyString()))
                .thenReturn(new StoredOrderArtifacts(
                        "financial-orders-bucket",
                        "incoming/json/2026-11-24/ORD-1001.json",
                        "incoming/csv/ORD-1001.csv"
                ));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHeaders(Map.of("X-Correlation-Id", "corr-999"))
                .withBody("""
                        {
                          "orderId": "ORD-1001",
                          "customerId": "CUST-500",
                          "ticker": "nvda",
                          "side": "buy",
                          "quantity": 10,
                          "unitPrice": 125.50,
                          "currency": "usd"
                        }
                        """);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, lambdaContext);

        assertEquals(201, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"ACCEPTED\""));
        assertTrue(response.getBody().contains("\"orderId\":\"ORD-1001\""));
        assertTrue(response.getBody().contains("\"notionalAmount\":1255.00"));
        verify(storageService).storeOrder(any(OrderRequest.class), anyString());
    }

    @Test
    @DisplayName("Should return 400 Bad Request and not invoke S3 when order payload has invalid quantity or currency")
    void shouldRejectInvalidOrderPayload() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody("""
                        {
                          "orderId": "ORD-BAD",
                          "customerId": "",
                          "ticker": "AAPL",
                          "side": "HOLD",
                          "quantity": -5,
                          "unitPrice": 0,
                          "currency": "GBP"
                        }
                        """);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, lambdaContext);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("\"status\":\"VALIDATION_ERROR\""));
        verify(storageService, never()).storeOrder(any(), anyString());
    }
}
