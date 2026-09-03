package com.mohammadmurrar.leadflow.workspace;

import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class CurrentWorkspace {
    private final WorkspaceRepository workspaces;

    public CurrentWorkspace(WorkspaceRepository workspaces) {
        this.workspaces = workspaces;
    }

    @Transactional(readOnly = true)
    public Workspace requireActive() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw denied();
        }
        UUID workspaceId = principal.workspaceId();
        if (workspaceId == null) throw denied();
        return workspaces.findByIdAndStatus(workspaceId, WorkspaceStatus.ACTIVE).orElseThrow(CurrentWorkspace::denied);
    }

    public UUID requireActiveId() {
        return requireActive().getId();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access is denied");
    }
}
