package com.cloudmentor.orders.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight structured JSON logger tailored for AWS CloudWatch Logs Insights.
 * Emits single-line JSON entries without pulling heavy logging frameworks into the Lambda artifact.
 */
public final class StructuredJsonLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final String serviceName;

    public StructuredJsonLogger(String serviceName) {
        this.serviceName = serviceName;
    }

    public void info(String eventType, String correlationId, String message, Map<String, Object> metadata) {
        log("INFO", eventType, correlationId, message, metadata, null);
    }

    public void warn(String eventType, String correlationId, String message, Map<String, Object> metadata) {
        log("WARN", eventType, correlationId, message, metadata, null);
    }

    public void error(String eventType, String correlationId, String message, Map<String, Object> metadata, Throwable error) {
        log("ERROR", eventType, correlationId, message, metadata, error);
    }

    private void log(
            String level,
            String eventType,
            String correlationId,
            String message,
            Map<String, Object> metadata,
            Throwable error
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("level", level);
        payload.put("service", serviceName);
        payload.put("eventType", eventType);
        payload.put("correlationId", correlationId != null ? correlationId : "N/A");
        payload.put("message", message);
        if (metadata != null && !metadata.isEmpty()) {
            payload.put("metadata", metadata);
        }
        if (error != null) {
            payload.put("errorClass", error.getClass().getName());
            payload.put("errorMessage", error.getMessage());
        }

        try {
            System.out.println(MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            System.out.printf("{\"level\":\"ERROR\",\"service\":\"%s\",\"message\":\"Failed to serialize log\"}\n", serviceName);
        }
    }
}
