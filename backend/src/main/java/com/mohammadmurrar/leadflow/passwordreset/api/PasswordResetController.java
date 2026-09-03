package com.mohammadmurrar.leadflow.passwordreset.api;

import com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {
    private final PasswordResetRequestService requests;
    private final PasswordResetConfirmationService confirmations;

    public PasswordResetController(PasswordResetRequestService requests,
            PasswordResetConfirmationService confirmations) {
        this.requests = requests;
        this.confirmations = confirmations;
    }

    @PostMapping(value = "/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        requests.request(request.email(), Instant.now());
        return noContent();
    }

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> confirm(
            @Valid @RequestBody PasswordResetConfirmationRequest request) {
        confirmations.confirm(request.token(), request.newPassword());
        return noContent();
    }

    private ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
