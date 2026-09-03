package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class AuthenticatedPrincipal implements UserDetails, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID id;
    private final String email;
    private final String displayName;
    private final UserRole role;
    private final UUID workspaceId;
    private final String passwordHash;
    private final boolean enabled;

    public AuthenticatedPrincipal(UUID id, String email, String displayName, UserRole role,
            UUID workspaceId, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.workspaceId = workspaceId;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    private AuthenticatedPrincipal(UUID id, String email, String displayName, UserRole role,
            UUID workspaceId, boolean enabled) {
        this(id, email, displayName, role, workspaceId, null, enabled);
    }

    public AuthenticatedPrincipal withoutCredentials() {
        return new AuthenticatedPrincipal(id, email, displayName, role, workspaceId, enabled);
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public UserRole role() { return role; }
    public UUID workspaceId() { return workspaceId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
}
