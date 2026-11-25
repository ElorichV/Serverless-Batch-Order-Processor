package com.cloudmentor.orders.batch.config;

import com.cloudmentor.orders.batch.batch.FinancialOrderItemProcessor;
import com.cloudmentor.orders.batch.batch.OrderSkipListener;
import com.cloudmentor.orders.batch.batch.PostgresOrderItemWriter;
import com.cloudmentor.orders.batch.batch.S3OrderCsvItemReader;
import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import com.cloudmentor.orders.batch.domain.ProcessedOrderEntity;
import com.cloudmentor.orders.batch.dto.OrderCsvRow;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Spring Batch 5 Configuration using the Chunk-Oriented Processing pattern:
 * ItemReader (S3 CSV) -> ItemProcessor (Commission/Tax/Rules) -> ItemWriter (PostgreSQL)
 * with FaultTolerant Retry and Skip policies.
 */
@Configuration
public class OrderBatchJobConfig {

    @Bean
    @StepScope
    public S3OrderCsvItemReader s3OrderCsvItemReader(
            S3Client s3Client,
            @Value("#{jobParameters['s3Bucket'] ?: '${app.aws.s3.bucket-name}'}") String bucketName,
            @Value("#{jobParameters['s3Prefix'] ?: '${app.aws.s3.csv-prefix}'}") String s3Prefix
    ) {
        return new S3OrderCsvItemReader(s3Client, bucketName, s3Prefix);
    }

    @Bean
    public Step settleOrdersChunkStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            S3OrderCsvItemReader itemReader,
            FinancialOrderItemProcessor itemProcessor,
            PostgresOrderItemWriter itemWriter,
            OrderSkipListener skipListener,
            @Value("${app.batch.chunk-size:50}") int chunkSize,
            @Value("${app.batch.skip-limit:100}") int skipLimit,
            @Value("${app.batch.retry-limit:3}") int retryLimit
    ) {
        return new StepBuilder("settleOrdersChunkStep", jobRepository)
                .<OrderCsvRow, ProcessedOrderEntity>chunk(chunkSize, transactionManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(InvalidOrderRecordException.class)
                .retryLimit(retryLimit)
                .retry(SdkClientException.class)
                .retry(TransientDataAccessException.class)
                .retry(CannotAcquireLockException.class)
                .listener(skipListener)
                .build();
    }

    @Bean
    public Job financialOrderSettlementJob(JobRepository jobRepository, Step settleOrdersChunkStep) {
        return new JobBuilder("financialOrderSettlementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(settleOrdersChunkStep)
                .build();
    }
}
