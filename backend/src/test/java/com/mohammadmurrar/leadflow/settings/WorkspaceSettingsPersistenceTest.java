package com.mohammadmurrar.leadflow.settings;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class WorkspaceSettingsPersistenceTest {
    @Autowired WorkspaceSettingsRepository repository;
    @Autowired WorkspaceSettingsService service;

    @Test
    void databaseEnforcesOneSingletonAndInitializationIsIdempotent() {
        repository.deleteAll();
        service.findWorkspace();
        service.findWorkspace();
        assertThat(repository.count()).isOne();
        assertThat(repository.initializeIfMissing(UUID.randomUUID())).isZero();
        assertThat(repository.count()).isOne();
    }

    @Test
    void optimisticVersionAdvancesOnlyForRealUpdates() {
        repository.deleteAll();
        var initial = service.findWorkspace();
        var noOp = service.update(new com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest(
                initial.version(), "My Workspace", null, null));
        assertThat(noOp.version()).isEqualTo(initial.version());
        assertThat(noOp.updatedAt()).isEqualTo(initial.updatedAt());

        var changed = service.update(new com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest(
                initial.version(), "Changed Workspace", null, null));
        assertThat(changed.version()).isGreaterThan(initial.version());
        assertThat(changed.updatedAt()).isAfterOrEqualTo(initial.updatedAt());
    }
}
