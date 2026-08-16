package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LeadLifecyclePersistenceTest {
    @Autowired LeadRepository repository;
    @Autowired LeadService service;

    @Test
    void realTransitionChangesUpdatedAtAndVersionWhileIdempotentRequestDoesNot() {
        Lead lead = Lead.create("Persistence Lead", "persistence-lifecycle@example.com", null,
                "Persistence Co", "Lifecycle testing", new BigDecimal("3000.00"),
                LocalDate.parse("2026-09-01"), "A detailed persistence lifecycle request.", "test");
        lead.startQualification();
        lead.applyQualification(89, LeadPriority.HIGH, "Enterprise",
                "Stable qualification summary", "Stable recommended reply");
        lead = repository.saveAndFlush(lead);

        long initialVersion = lead.getVersion();
        Instant createdAt = lead.getCreatedAt();
        Instant initialUpdatedAt = lead.getUpdatedAt();
        LeadResponse transitioned = service.changeStatus(lead.getId(), LeadStatus.CONTACTED, initialVersion);

        assertThat(transitioned.version()).isEqualTo(initialVersion + 1);
        assertThat(transitioned.createdAt()).isEqualTo(createdAt);
        assertThat(transitioned.updatedAt()).isAfter(initialUpdatedAt);
        assertThat(transitioned.qualificationScore()).isEqualTo(89);
        assertThat(transitioned.aiSummary()).isEqualTo("Stable qualification summary");

        LeadResponse repeated = service.changeStatus(lead.getId(), LeadStatus.CONTACTED, transitioned.version());
        assertThat(repeated.version()).isEqualTo(transitioned.version());
        assertThat(repeated.updatedAt()).isEqualTo(transitioned.updatedAt());
        assertThat(repeated.createdAt()).isEqualTo(createdAt);
    }
}
