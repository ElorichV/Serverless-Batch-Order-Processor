package com.cloudmentor.orders.batch.dto;

import java.time.Instant;

/**
 * Response DTO (Java 21 Record) returned after launching a Spring Batch settlement job.
 */
public record JobExecutionResponseDto(
        Long jobExecutionId,
        String jobName,
        String status,
        String exitCode,
        String s3Bucket,
        String s3Prefix,
        int readCount,
        int writeCount,
        int skipCount,
        Instant triggeredAt
) {}
