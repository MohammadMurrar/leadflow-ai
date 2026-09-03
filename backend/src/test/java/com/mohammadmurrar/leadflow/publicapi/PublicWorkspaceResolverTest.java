package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PublicWorkspaceResolverTest {
    @Test
    void resolvesOnlyTheExactActiveCanonicalSlug() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        Workspace workspace = Workspace.create(UUID.randomUUID(), "acme-consulting", "Acme",
                WorkspaceStatus.ACTIVE);
        when(repository.findByPublicSlugAndStatus("acme-consulting", WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(workspace));

        assertThat(new PublicWorkspaceResolver(repository).resolve("acme-consulting"))
                .isSameAs(workspace);
        verify(repository).findByPublicSlugAndStatus("acme-consulting", WorkspaceStatus.ACTIVE);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "A", "Acme", "-acme", "acme-", "acme--tenant/other",
            "acme%2Fother", "acme%252Fother", ".", "..", "acme;admin=true", "acme tenant",
            "acme\u0000tenant", "\u0430cme", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void malformedSlugsFailBeforeRepositoryAccess(String slug) {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);

        assertThatThrownBy(() -> new PublicWorkspaceResolver(repository).resolve(slug))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Public inquiry is unavailable");
        verifyNoInteractions(repository);
    }

    @Test
    void unknownPendingAndSuspendedStatesAreIndistinguishable() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        when(repository.findByPublicSlugAndStatus("unavailable", WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PublicWorkspaceResolver(repository).resolve("unavailable"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Public inquiry is unavailable")
                .hasMessageNotContaining("PENDING")
                .hasMessageNotContaining("SUSPENDED");
    }
}
