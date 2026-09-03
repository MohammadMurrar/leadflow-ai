package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyPublicWorkspaceResolverTest {
    @Test
    void resolvesOnlyTheActiveExactLegacySlug() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        Workspace workspace = Workspace.create(java.util.UUID.randomUUID(), "leadflow-ai",
                "Legacy", WorkspaceStatus.ACTIVE);
        when(repository.findByPublicSlugAndStatus("leadflow-ai", WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(workspace));

        assertThat(new LegacyPublicWorkspaceResolver(new PublicWorkspaceResolver(repository)).resolve())
                .isSameAs(workspace);
        verify(repository).findByPublicSlugAndStatus("leadflow-ai", WorkspaceStatus.ACTIVE);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void missingPendingOrSuspendedLegacyWorkspaceFailsWithSafeGenericError() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        when(repository.findByPublicSlugAndStatus("leadflow-ai", WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LegacyPublicWorkspaceResolver(
                new PublicWorkspaceResolver(repository)).resolve())
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Public inquiry is unavailable")
                .hasMessageNotContaining("ACTIVE")
                .hasMessageNotContaining("SUSPENDED")
                .hasMessageNotContaining("PENDING");
    }
}
