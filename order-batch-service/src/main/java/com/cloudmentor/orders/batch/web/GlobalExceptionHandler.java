package com.cloudmentor.orders.batch.web;

import com.cloudmentor.orders.batch.domain.InvalidOrderRecordException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler (@RestControllerAdvice) returning RFC 7807 ProblemDetail responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (a, b) -> a
                ));

        log.warn("Request validation failed path={} errors={}", request.getRequestURI(), fieldErrors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields failed validation"
        );
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://cloudmentor.com/errors/validation-error"));
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(InvalidOrderRecordException.class)
    public ProblemDetail handleInvalidOrderRecord(InvalidOrderRecordException ex, HttpServletRequest request) {
        log.warn("Invalid order record encountered orderId={} s3Key={} message={}",
                ex.getOrderId(), ex.getSourceS3Key(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Invalid Financial Order Record");
        problem.setType(URI.create("https://cloudmentor.com/errors/invalid-order-record"));
        problem.setProperty("orderId", ex.getOrderId());
        problem.setProperty("sourceS3Key", ex.getSourceS3Key());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleBatchExecutionFailure(IllegalStateException ex, HttpServletRequest request) {
        log.error("Batch execution error on path={}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Batch Job Execution Failure");
        problem.setType(URI.create("https://cloudmentor.com/errors/batch-execution-failure"));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on path={}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred while processing the request"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://cloudmentor.com/errors/internal-error"));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
