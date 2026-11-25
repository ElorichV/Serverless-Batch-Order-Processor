package com.cloudmentor.orders.batch.service;

import com.cloudmentor.orders.batch.dto.JobExecutionResponseDto;
import com.cloudmentor.orders.batch.dto.JobTriggerRequest;
import com.cloudmentor.orders.batch.dto.ProcessedOrderDto;
import com.cloudmentor.orders.batch.repository.ProcessedOrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that coordinates Spring Batch job execution and read-only queries.
 */
@Service
public class BatchJobLauncherService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobLauncherService.class);

    private final JobLauncher jobLauncher;
    private final Job financialOrderSettlementJob;
    private final ProcessedOrderRepository processedOrderRepository;
    private final String defaultBucket;
    private final String defaultPrefix;

    public BatchJobLauncherService(
            JobLauncher jobLauncher,
            Job financialOrderSettlementJob,
            ProcessedOrderRepository processedOrderRepository,
            @Value("${app.aws.s3.bucket-name}") String defaultBucket,
            @Value("${app.aws.s3.csv-prefix}") String defaultPrefix
    ) {
        this.jobLauncher = jobLauncher;
        this.financialOrderSettlementJob = financialOrderSettlementJob;
        this.processedOrderRepository = processedOrderRepository;
        this.defaultBucket = defaultBucket;
        this.defaultPrefix = defaultPrefix;
    }

    public JobExecutionResponseDto launchOrderSettlementJob(JobTriggerRequest request) {
        String resolvedPrefix = Optional.ofNullable(request)
                .map(JobTriggerRequest::s3Prefix)
                .filter(p -> !p.isBlank())
                .orElse(defaultPrefix);

        String triggeredBy = Optional.ofNullable(request)
                .map(JobTriggerRequest::triggeredBy)
                .filter(t -> !t.isBlank())
                .orElse("MANUAL_REST_API");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("s3Bucket", defaultBucket)
                    .addString("s3Prefix", resolvedPrefix)
                    .addString("triggeredBy", triggeredBy)
                    .addLong("requestedAtEpochMs", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Launching Spring Batch financialOrderSettlementJob bucket={} prefix={} triggeredBy={}",
                    defaultBucket, resolvedPrefix, triggeredBy);

            JobExecution execution = jobLauncher.run(financialOrderSettlementJob, params);

            int readCount = execution.getStepExecutions().stream().mapToInt(StepExecution::getReadCount).sum();
            int writeCount = execution.getStepExecutions().stream().mapToInt(StepExecution::getWriteCount).sum();
            int skipCount = execution.getStepExecutions().stream().mapToInt(StepExecution::getSkipCount).sum();

            return new JobExecutionResponseDto(
                    execution.getId(),
                    execution.getJobInstance().getJobName(),
                    execution.getStatus().name(),
                    execution.getExitStatus().getExitCode(),
                    defaultBucket,
                    resolvedPrefix,
                    readCount,
                    writeCount,
                    skipCount,
                    Instant.now()
            );
        } catch (Exception ex) {
            log.error("Failed to launch financialOrderSettlementJob", ex);
            throw new IllegalStateException("Could not execute Spring Batch job: " + ex.getMessage(), ex);
        }
    }

    /**
     * Nightly scheduled batch settlement at 02:00 AM UTC.
     */
    @Scheduled(cron = "${app.batch.nightly-cron:0 0 2 * * *}")
    public void runNightlyScheduledSettlement() {
        log.info("Starting nightly scheduled financial order settlement batch job");
        launchOrderSettlementJob(new JobTriggerRequest(defaultPrefix, "NIGHTLY_SCHEDULER"));
    }

    @Transactional(readOnly = true)
    public List<ProcessedOrderDto> getRecentProcessedOrders() {
        return processedOrderRepository.findTop100ByOrderByProcessedAtDesc()
                .stream()
                .map(ProcessedOrderDto::fromEntity)
                .toList();
    }
}
