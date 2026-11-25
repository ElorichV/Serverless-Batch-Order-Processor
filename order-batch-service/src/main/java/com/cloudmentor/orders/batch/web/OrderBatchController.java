package com.cloudmentor.orders.batch.web;

import com.cloudmentor.orders.batch.dto.JobExecutionResponseDto;
import com.cloudmentor.orders.batch.dto.JobTriggerRequest;
import com.cloudmentor.orders.batch.dto.ProcessedOrderDto;
import com.cloudmentor.orders.batch.service.BatchJobLauncherService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST Controller exposing manual trigger and query endpoints for the Spring Batch Order Processor.
 */
@RestController
@RequestMapping("/api/v1/batch")
public class OrderBatchController {

    private final BatchJobLauncherService batchJobLauncherService;

    public OrderBatchController(BatchJobLauncherService batchJobLauncherService) {
        this.batchJobLauncherService = batchJobLauncherService;
    }

    @PostMapping("/jobs/orders")
    public ResponseEntity<JobExecutionResponseDto> triggerOrderSettlementJob(
            @Valid @RequestBody(required = false) JobTriggerRequest request
    ) {
        JobExecutionResponseDto response = batchJobLauncherService.launchOrderSettlementJob(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<ProcessedOrderDto>> listProcessedOrders() {
        return ResponseEntity.ok(batchJobLauncherService.getRecentProcessedOrders());
    }
}
