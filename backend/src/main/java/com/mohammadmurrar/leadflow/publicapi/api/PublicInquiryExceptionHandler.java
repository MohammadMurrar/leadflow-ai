package com.mohammadmurrar.leadflow.publicapi.api;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PublicInquiryController.class)
public class PublicInquiryExceptionHandler {
    private static final String VALIDATION_MESSAGE = "Request validation failed";
    private static final Map<String, String> SAFE_FIELD_MESSAGES = Map.of(
            "fullName", "Enter a full name of 100 characters or fewer",
            "email", "Enter a valid email address of 180 characters or fewer",
            "phone", "Phone number must contain 30 characters or fewer",
            "company", "Company must contain 140 characters or fewer",
            "serviceId", "Choose an available service",
            "estimatedBudget", "Enter a non-negative budget with up to 10 integer digits and 2 decimal places",
            "desiredStartDate", "Choose today or a future date",
            "message", "Enter a message containing between 20 and 3000 characters",
            "website", "Submission could not be accepted"
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<PublicApiError> validation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> safeDetail(error.getField()))
                .filter(detail -> detail != null)
                .distinct()
                .sorted()
                .toList();
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PublicApiError> unreadableRequest(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, List.of());
    }

    @ExceptionHandler({NotFoundException.class, ConflictException.class})
    ResponseEntity<PublicApiError> unavailableService(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE,
                List.of(safeDetail("serviceId")));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<PublicApiError> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "The inquiry could not be processed", List.of());
    }

    private String safeDetail(String field) {
        String message = SAFE_FIELD_MESSAGES.get(field);
        return message == null ? null : field + ": " + message;
    }

    private ResponseEntity<PublicApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new PublicApiError(Instant.now(), status.value(), message, details));
    }

    record PublicApiError(Instant timestamp, int status, String message, List<String> details) {}
}
