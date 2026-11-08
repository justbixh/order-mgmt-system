package com.example.ordermgmt.order.web;

import com.example.ordermgmt.order.IdempotencyConflictException;
import com.example.ordermgmt.order.domain.OrderNotCancellableException;
import com.example.ordermgmt.order.domain.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into RFC 9457 "problem details" JSON responses
 * ({@code application/problem+json}). Validation errors (HTTP 400) are produced by Spring's own
 * problem-details support, enabled with {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    ProblemDetail notCancellable(OrderNotCancellableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, OptimisticLockingFailureException.class})
    ProblemDetail conflict(RuntimeException e) {
        log.info("Concurrent modification: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicted with a concurrent change; please retry");
    }
}
