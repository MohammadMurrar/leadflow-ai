package com.mohammadmurrar.leadflow.common;

import com.mohammadmurrar.leadflow.dashboard.DashboardService.InvalidDashboardRangeException;
import com.mohammadmurrar.leadflow.lead.LeadService.InvalidLeadSearchException;
import com.mohammadmurrar.leadflow.lead.LeadService.InvalidLeadFilterException;
import com.mohammadmurrar.leadflow.lead.LeadService.InvalidLeadServiceSelectionException;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService.InvalidServiceQueryException;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService.InvalidServiceRequestException;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService.InvalidWorkspaceSettingsException;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService.WorkspaceSettingsUnavailableException;
import com.mohammadmurrar.leadflow.auth.InvalidCredentialsException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", List.of());
    }
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request body contains an invalid value", List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> optimisticConflict(OptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "Lead changed elsewhere. Refresh and try again", List.of());
    }

    @ExceptionHandler(InvalidDashboardRangeException.class)
    ResponseEntity<ApiError> invalidDashboardRange(InvalidDashboardRangeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidLeadSearchException.class)
    ResponseEntity<ApiError> invalidLeadSearch(InvalidLeadSearchException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidLeadFilterException.class)
    ResponseEntity<ApiError> invalidLeadFilter(InvalidLeadFilterException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidLeadServiceSelectionException.class)
    ResponseEntity<ApiError> invalidLeadServiceSelection(InvalidLeadServiceSelectionException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler({InvalidServiceQueryException.class, InvalidServiceRequestException.class})
    ResponseEntity<ApiError> invalidServiceRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidWorkspaceSettingsException.class)
    ResponseEntity<ApiError> invalidWorkspaceSettings(InvalidWorkspaceSettingsException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(WorkspaceSettingsUnavailableException.class)
    ResponseEntity<ApiError> workspaceSettingsUnavailable(WorkspaceSettingsUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request parameter contains an invalid value", List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message, details));
    }

    record ApiError(Instant timestamp, int status, String message, List<String> details) {}
}
