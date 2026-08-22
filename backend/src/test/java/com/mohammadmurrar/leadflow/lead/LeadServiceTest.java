package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.lead.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.service.*;
import com.mohammadmurrar.leadflow.service.api.UpdateServiceRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class LeadServiceTest {
    @Autowired LeadService service;
    @Autowired ServiceOfferingRepository serviceOfferings;
    @Autowired ServiceOfferingService serviceOfferingService;
    @Autowired LeadRepository leads;

    @Test
    void createsLeadReadyForQualification() {
        LeadResponse result = service.create(request("person@example.com"));
        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFYING);
        assertThat(result.priority()).isEqualTo(LeadPriority.UNASSESSED);
        assertThat(result.email()).isEqualTo("person@example.com");
    }

    @Test
    void rejectsDuplicateEmailWithinTwentyFourHours() {
        service.create(request("duplicate@example.com"));
        long leadCount = leads.count();

        assertThatThrownBy(() -> service.create(request("  DUPLICATE@EXAMPLE.COM  ")))
                .isInstanceOf(DuplicateLeadException.class)
                .hasMessage("A recent lead already exists for this email")
                .hasMessageNotContaining("duplicate@example.com");
        assertThat(leads.count()).isEqualTo(leadCount);
    }

    @Test
    void appliesAutomationQualification() {
        LeadResponse lead = service.create(request("qualified@example.com"));
        LeadResponse result = service.qualify(lead.id(), new QualificationRequest(
                88, LeadPriority.HIGH, "Backend Development",
                "Qualified lead with a defined integration requirement.",
                "Thanks for sharing your requirements. Let us schedule a discovery call."
        ));
        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(result.version()).isGreaterThan(lead.version());
        assertThat(result.qualificationScore()).isEqualTo(88);
        assertThat(result.priority()).isEqualTo(LeadPriority.HIGH);
    }

    @Test
    void catalogLinkedCreationUsesAuthoritativeSnapshotAndRenamePreservesIt() {
        ServiceOffering offering = serviceOfferings.saveAndFlush(
                ServiceOffering.create("AI Lead Automation", "Catalog service"));
        LeadResponse lead = service.create(linkedRequest("linked@example.com", offering.getId(), null));
        Lead persisted = leads.findById(lead.id()).orElseThrow();
        assertThat(lead.requestedService()).isEqualTo("AI Lead Automation");
        assertThat(persisted.getService().getId()).isEqualTo(offering.getId());

        serviceOfferingService.update(offering.getId(),
                new UpdateServiceRequest(offering.getVersion(), "AI Revenue Automation", "Renamed"));
        assertThat(service.findById(lead.id()).requestedService()).isEqualTo("AI Lead Automation");
    }

    @Test
    void rejectsInvalidCatalogSelectionAndPreservesFreeTextCompatibility() {
        LeadResponse legacy = service.create(request("legacy-compatible@example.com"));
        assertThat(legacy.requestedService()).isEqualTo("Backend API");
        assertThat(leads.findById(legacy.id()).orElseThrow().getService()).isNull();

        assertThatThrownBy(() -> service.create(linkedRequest("unknown-service@example.com", UUID.randomUUID(), null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(linkedRequest("both@example.com", UUID.randomUUID(), "Free text")))
                .isInstanceOf(LeadService.InvalidLeadServiceSelectionException.class);
        assertThatThrownBy(() -> service.create(linkedRequest("neither@example.com", null, null)))
                .isInstanceOf(LeadService.InvalidLeadServiceSelectionException.class);

        ServiceOffering inactive = serviceOfferings.saveAndFlush(ServiceOffering.create("Inactive", null));
        serviceOfferingService.deactivate(inactive.getId(), inactive.getVersion());
        assertThatThrownBy(() -> service.create(linkedRequest("inactive@example.com", inactive.getId(), null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("no longer active");
    }

    private CreateLeadRequest request(String email) {
        return new CreateLeadRequest("Alex Morgan", email, "+1 555 0100", "Northstar Services",
                "Backend API", new BigDecimal("4500.00"), LocalDate.now().plusDays(14),
                "We need a secure customer portal API integrated with our CRM.", "portfolio-demo");
    }

    private CreateLeadRequest linkedRequest(String email, UUID serviceId, String requestedService) {
        return new CreateLeadRequest("Alex Morgan", email, "+1 555 0100", "Northstar Services",
                serviceId, requestedService, new BigDecimal("4500.00"), LocalDate.now().plusDays(14),
                "We need a secure customer portal API integrated with our CRM.", "portfolio-demo");
    }
}
