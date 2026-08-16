package com.mohammadmurrar.leadflow.auth.api;

import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserRole;
import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role
) {
    public static AuthenticatedUserResponse from(AuthenticatedPrincipal principal) {
        return new AuthenticatedUserResponse(principal.id(), principal.email(),
                principal.displayName(), principal.role());
    }
}
