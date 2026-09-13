package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityStateService {
    private final EntityManager entities;

    public IdentityStateService(EntityManager entities) { this.entities = entities; }

    // A scalar indexed lookup bypasses serialized principals and the entity identity map.
    @Transactional(readOnly = true)
    public boolean isActive(AuthenticatedPrincipal principal) {
        if (principal.id() == null || principal.workspaceId() == null || principal.role() != UserRole.ADMIN) return false;
        return entities.createQuery("""
                select count(user) from User user join user.workspace workspace
                where user.id = :userId and workspace.id = :workspaceId
                  and user.normalizedEmail = :email and user.enabled = true
                  and user.role = com.mohammadmurrar.leadflow.user.UserRole.ADMIN
                  and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
                """, Long.class)
                .setParameter("userId", principal.id()).setParameter("workspaceId", principal.workspaceId())
                .setParameter("email", principal.email()).getSingleResult() == 1L;
    }

    // Lock order is user -> workspace -> reset request. Refresh is essential when a
    // previous nonlocking lookup populated the persistence context under MySQL RR.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockActiveAdministrator(User user) {
        try {
            entities.refresh(user, LockModeType.PESSIMISTIC_WRITE);
            if (!user.isEnabled() || user.getRole() != UserRole.ADMIN || user.getWorkspace() == null) return false;
            Workspace workspace = user.getWorkspace();
            entities.refresh(workspace, LockModeType.PESSIMISTIC_WRITE);
            return workspace.getStatus() == WorkspaceStatus.ACTIVE;
        } catch (EntityNotFoundException exception) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshLocked(Object entity) {
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    }
}
