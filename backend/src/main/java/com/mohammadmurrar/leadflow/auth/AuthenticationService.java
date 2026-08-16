package com.mohammadmurrar.leadflow.auth;

import com.mohammadmurrar.leadflow.auth.api.AuthenticatedUserResponse;
import com.mohammadmurrar.leadflow.auth.api.LoginRequest;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final UserService userService;

    public AuthenticationService(AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contextRepository, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.userService = userService;
    }

    public AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            if (request.email() == null || request.email().isBlank() || request.email().length() > 254
                    || request.password() == null || request.password().length() < 12
                    || request.password().length() > 256) {
                throw new IllegalArgumentException("Invalid credentials");
            }
            Authentication authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim(), request.password()));
            sessionStrategy.onAuthentication(authenticated, httpRequest, httpResponse);
            AuthenticatedPrincipal principal = ((AuthenticatedPrincipal) authenticated.getPrincipal())
                    .withoutCredentials();
            Authentication sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(sessionAuthentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, httpRequest, httpResponse);
            if (authenticated instanceof CredentialsContainer credentials) credentials.eraseCredentials();
            userService.recordSuccessfulLogin(principal.id());
            return AuthenticatedUserResponse.from(principal);
        } catch (AuthenticationException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            throw new InvalidCredentialsException();
        }
    }
}
