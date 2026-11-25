package com.cloudmentor.orders.batch.batch;

import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import com.cloudmentor.orders.batch.dto.OrderCsvRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Chunk-oriented Spring Batch ItemReader that discovers and streams CSV files from an S3 bucket prefix.
 *
 * Mentor Note:
 * Instantiated per Step (@StepScope in OrderBatchJobConfig) so each job execution maintains isolated cursor state.
 */
public class S3OrderCsvItemReader implements ItemReader<OrderCsvRow> {

    private static final Logger log = LoggerFactory.getLogger(S3OrderCsvItemReader.class);

    private record RawCsvLine(String s3Key, int lineNumber, String rawLine) {}

    private final S3Client s3Client;
    private final String bucketName;
    private final String s3Prefix;
    private final Queue<RawCsvLine> bufferedLines = new ArrayDeque<>();
    private boolean initialized = false;

    public S3OrderCsvItemReader(S3Client s3Client, String bucketName, String s3Prefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.s3Prefix = s3Prefix;
    }

    @Override
    public OrderCsvRow read() {
        if (!initialized) {
            loadCsvLinesFromS3();
            initialized = true;
        }

        RawCsvLine next = bufferedLines.poll();
        if (next == null) {
            return null; // Signals end of dataset to Spring Batch Step
        }

        return parseCsvLine(next);
    }

    private void loadCsvLinesFromS3() {
        log.info("Scanning S3 bucket={} prefix={} for CSV order files", bucketName, s3Prefix);

        List<S3Object> csvObjects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(s3Prefix)
                        .build())
                .contents()
                .stream()
                .filter(obj -> obj.key().endsWith(".csv"))
                .toList();

        log.info("Found {} CSV files in S3 bucket={} prefix={}", csvObjects.size(), bucketName, s3Prefix);

        for (S3Object s3Object : csvObjects) {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Object.key())
                    .build());

            List<String> lines = bytes.asString(StandardCharsets.UTF_8).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.isEmpty() || trimmed.startsWith("orderId,")) {
                    continue;
                }
                bufferedLines.offer(new RawCsvLine(s3Object.key(), i + 1, trimmed));
            }
        }
    }

    private OrderCsvRow parseCsvLine(RawCsvLine line) {
        String[] cols = line.rawLine().split(",");
        if (cols.length < 7) {
            throw new InvalidOrderRecordException(
                    "MALFORMED_LINE_" + line.lineNumber(),
                    line.s3Key(),
                    "CSV line has fewer than 7 columns: " + line.rawLine()
            );
        }

        try {
            return new OrderCsvRow(
                    cols[0].trim(),
                    cols[1].trim(),
                    cols[2].trim(),
                    cols[3].trim(),
                    Integer.parseInt(cols[4].trim()),
                    new BigDecimal(cols[5].trim()),
                    cols[6].trim(),
                    line.s3Key(),
                    line.lineNumber()
            );
        } catch (NumberFormatException ex) {
            throw new InvalidOrderRecordException(
                    cols[0].trim(),
                    line.s3Key(),
                    "Invalid numeric quantity or unitPrice at line " + line.lineNumber() + ": " + ex.getMessage()
            );
        }
    }
}
