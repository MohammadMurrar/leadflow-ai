package com.mohammadmurrar.leadflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

final class ActiveIdentityFilter extends OncePerRequestFilter {
    private final IdentityStateService identities;
    private final org.springframework.security.web.util.matcher.RequestMatcher protectedRequests;
    ActiveIdentityFilter(IdentityStateService identities,
            org.springframework.security.web.util.matcher.RequestMatcher protectedRequests) {
        this.identities = identities;
        this.protectedRequests = protectedRequests;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedRequests.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && (!(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)
                    || !identities.isActive(principal))) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
        }
        // Existing authorization/entry-point rules give protected requests their usual
        // generic 401; public and CSRF-protected recovery flows remain available.
        chain.doFilter(request, response);
    }
}
