package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadSearchServiceTest {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    @Mock LeadRepository repository;
    @Mock LeadQualificationWebhookClient webhookClient;
    @Mock NotificationService notificationService;
    @Mock QualificationAttemptService qualificationAttemptService;
    @Mock ServiceOfferingService serviceOfferingService;
    @Mock CurrentWorkspace currentWorkspace;

    @Test
    void omittedBlankAndWhitespaceSearchUseExistingUnfilteredQuery() {
        LeadService service = service();
        PageRequest pageable = PageRequest.of(0, 20);
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findAllByWorkspaceId(WORKSPACE_ID, pageable)).thenReturn(Page.empty());

        service.findAll(null, null, null, pageable);
        service.findAll(null, null, "", pageable);
        service.findAll(null, null, "   ", pageable);

        verify(repository, org.mockito.Mockito.times(3)).findAllByWorkspaceId(WORKSPACE_ID, pageable);
    }

    @Test
    void trimsAndNormalizesSearchBeforeCombiningItWithStatus() {
        LeadService service = service();
        PageRequest pageable = PageRequest.of(0, 10);
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.search(WORKSPACE_ID, "nova automation", LeadStatus.QUALIFIED, pageable))
                .thenReturn(Page.empty());

        Page<LeadResponse> result = service.findAll(
                LeadStatus.QUALIFIED, null, "  NoVa AuToMaTiOn  ", pageable);

        verify(repository).search(WORKSPACE_ID, "nova automation", LeadStatus.QUALIFIED, pageable);
        verifyNoInteractions(webhookClient, notificationService);
        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
    }

    @Test
    void rejectsSearchLongerThanOneHundredTrimmedCharacters() {
        LeadService service = service();

        assertThatThrownBy(() -> service.findAll(null, null, "x".repeat(101), PageRequest.of(0, 10)))
                .isInstanceOf(LeadService.InvalidLeadSearchException.class)
                .hasMessage("Search must not exceed 100 characters");

        verifyNoInteractions(repository);
    }

    private LeadService service() {
        return new LeadService(repository, notificationService, qualificationAttemptService, properties(),
                serviceOfferingService,
                org.mockito.Mockito.mock(com.mohammadmurrar.leadflow.email.EmailIntentService.class),
                currentWorkspace);
    }

    private QualificationReliabilityProperties properties() {
        return new QualificationReliabilityProperties(false, true, false,
                java.time.Duration.ofHours(1), 10, 3, java.time.Duration.ofSeconds(1),
                java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(1),
                java.time.Duration.ofMinutes(30), java.time.Duration.ofMinutes(30), 20);
    }
}
