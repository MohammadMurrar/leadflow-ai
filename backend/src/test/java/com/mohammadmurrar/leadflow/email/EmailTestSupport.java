package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class EmailTestSupport {
    private EmailTestSupport() {}

    static WorkspaceSettingsRepository usdSettingsRepository() {
        WorkspaceSettingsRepository repository = mock(WorkspaceSettingsRepository.class);
        WorkspaceSettings settings = mock(WorkspaceSettings.class);
        when(settings.getCurrency()).thenReturn("USD");
        when(repository.findByWorkspaceId(any())).thenReturn(Optional.of(settings));
        return repository;
    }
}
