package com.cloudmentor.orders.batch.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO (Java 21 Record) for triggering the Spring Batch job manually via REST API.
 */
public record JobTriggerRequest(
        @Size(max = 128, message = "s3Prefix cannot exceed 128 characters")
        @Pattern(regexp = "^[a-zA-Z0-9/_\\-.]*$", message = "s3Prefix contains invalid characters")
        String s3Prefix,
        String triggeredBy
) {}
