package com.mohammadmurrar.leadflow.passwordreset.api;

import com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService.InvalidPasswordException;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService.InvalidPasswordResetException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PasswordResetController.class)
public class PasswordResetExceptionHandler {
    private static final String VALIDATION_MESSAGE = "Request validation failed";

    @ExceptionHandler(InvalidPasswordResetException.class)
    ResponseEntity<PasswordResetApiError> invalidToken(InvalidPasswordResetException exception) {
        return error(HttpStatus.BAD_REQUEST, InvalidPasswordResetException.class.cast(exception).getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ResponseEntity<PasswordResetApiError> invalidPassword(InvalidPasswordException exception) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE,
                List.of("newPassword: " + exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<PasswordResetApiError> validation(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().getFieldErrors().stream()
                .anyMatch(error -> error.getField().equals("token"))) {
            return error(HttpStatus.BAD_REQUEST,
                    com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService
                            .INVALID_TOKEN_MESSAGE,
                    List.of());
        }
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> safeDetail(error.getField()))
                .distinct().sorted().toList();
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PasswordResetApiError> unreadable(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, VALIDATION_MESSAGE, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<PasswordResetApiError> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "The password reset could not be processed",
                List.of());
    }

    private String safeDetail(String field) {
        return switch (field) {
            case "email" -> "email: Enter a valid email address";
            case "token" -> "token: Password reset link is invalid";
            case "newPassword" -> "newPassword: Enter a valid new password";
            default -> "Request contains an invalid field";
        };
    }

    private ResponseEntity<PasswordResetApiError> error(HttpStatus status, String message,
            List<String> details) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new PasswordResetApiError(Instant.now(), status.value(), message, details));
    }

    record PasswordResetApiError(Instant timestamp, int status, String message,
                                 List<String> details) {}
}
