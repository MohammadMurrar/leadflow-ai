package com.mohammadmurrar.leadflow.auth.api;

import com.mohammadmurrar.leadflow.auth.AuthenticationService;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {
    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getToken(), token.getHeaderName()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticatedUserResponse> login(@RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authenticationService.login(request, httpRequest, httpResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AuthenticatedUserResponse.from(principal));
    }
}
