package com.cloudmentor.orders.api.service;

import com.cloudmentor.orders.api.model.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Service responsible for persisting validated financial orders into Amazon S3 (or LocalStack).
 * Stores both:
 * 1. A structured JSON artifact under incoming/json/ (consumed in real-time by EventBridge -> order-event-processor-lambda).
 * 2. A CSV artifact under incoming/csv/ (consumed in batch by Spring Batch chunk processor).
 */
public class S3OrderStorageService {

    public record StoredOrderArtifacts(String bucket, String jsonKey, String csvKey) {}

    private final S3Client s3Client;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public S3OrderStorageService() {
        this(
                buildDefaultS3Client(),
                Optional.ofNullable(System.getenv("ORDERS_BUCKET_NAME")).orElse("financial-orders-bucket"),
                new ObjectMapper()
        );
    }

    public S3OrderStorageService(S3Client s3Client, String bucketName, ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
    }

    public StoredOrderArtifacts storeOrder(OrderRequest order, String correlationId) throws Exception {
        String datePartition = LocalDate.now(ZoneOffset.UTC).toString();
        String jsonKey = String.format("incoming/json/%s/%s.json", datePartition, order.orderId());
        String csvKey = String.format("incoming/csv/%s.csv", order.orderId());

        byte[] jsonBytes = objectMapper.writeValueAsBytes(Map.of(
                "order", order,
                "notionalAmount", order.notionalAmount(),
                "correlationId", correlationId,
                "partitionDate", datePartition
        ));

        PutObjectRequest jsonPutRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(jsonKey)
                .contentType("application/json")
                .metadata(Map.of(
                        "correlation-id", correlationId,
                        "order-id", order.orderId(),
                        "customer-id", order.customerId()
                ))
                .build();
        s3Client.putObject(jsonPutRequest, RequestBody.fromBytes(jsonBytes));

        byte[] csvBytes = order.toCsvWithHeader().getBytes(StandardCharsets.UTF_8);
        PutObjectRequest csvPutRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(csvKey)
                .contentType("text/csv")
                .metadata(Map.of(
                        "correlation-id", correlationId,
                        "order-id", order.orderId()
                ))
                .build();
        s3Client.putObject(csvPutRequest, RequestBody.fromBytes(csvBytes));

        return new StoredOrderArtifacts(bucketName, jsonKey, csvKey);
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
