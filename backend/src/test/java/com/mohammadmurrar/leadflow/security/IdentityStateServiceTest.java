package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.*;
import com.mohammadmurrar.leadflow.workspace.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityStateServiceTest {
    private User user() {
        return User.createAdministrator(Workspace.create(UUID.randomUUID(), "synthetic", "Synthetic", WorkspaceStatus.ACTIVE),
                "synthetic@example.invalid", "Synthetic", UUID.randomUUID().toString());
    }
    @Test void missingWorkspaceAndDeletedUserFailClosed() {
        for (boolean missingWorkspace : new boolean[]{false, true}) {
            EntityManager entities = mock(EntityManager.class);
            User user = user();
            doThrow(new EntityNotFoundException()).when(entities).refresh(
                    missingWorkspace ? user.getWorkspace() : user, LockModeType.PESSIMISTIC_WRITE);
            assertThat(new IdentityStateService(entities).lockActiveAdministrator(user)).isFalse();
        }
    }
    @Test void userRefreshPrecedesWorkspaceLockAndUsesRefreshedEnabledState() {
        EntityManager entities = mock(EntityManager.class);
        User user = user();
        doAnswer(invocation -> { ReflectionTestUtils.setField(user, "enabled", false); return null; })
                .when(entities).refresh(user, LockModeType.PESSIMISTIC_WRITE);
        assertThat(new IdentityStateService(entities).lockActiveAdministrator(user)).isFalse();
        verify(entities, never()).refresh(user.getWorkspace(), LockModeType.PESSIMISTIC_WRITE);
    }
    @Test void workspaceRefreshUsesFreshStatusAndLocksInOneOrder() {
        for (WorkspaceStatus status : WorkspaceStatus.values()) {
            EntityManager entities = mock(EntityManager.class);
            User user = user();
            doAnswer(invocation -> { ReflectionTestUtils.setField(user.getWorkspace(), "status", status); return null; })
                    .when(entities).refresh(user.getWorkspace(), LockModeType.PESSIMISTIC_WRITE);
            assertThat(new IdentityStateService(entities).lockActiveAdministrator(user)).isEqualTo(status == WorkspaceStatus.ACTIVE);
            var order = inOrder(entities);
            order.verify(entities).refresh(user, LockModeType.PESSIMISTIC_WRITE);
            order.verify(entities).refresh(user.getWorkspace(), LockModeType.PESSIMISTIC_WRITE);
        }
    }
    @Test void nullSessionOwnershipNeverAuthorizes() {
        EntityManager entities = mock(EntityManager.class);
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(), "synthetic@example.invalid", "Synthetic",
                UserRole.ADMIN, null, null, true);
        assertThat(new IdentityStateService(entities).isActive(principal)).isFalse();
        verifyNoInteractions(entities);
    }
}
