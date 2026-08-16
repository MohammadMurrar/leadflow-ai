package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.LeadService;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LeadLifecycleApiTest {
    private static final UUID ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    @Autowired MockMvc mockMvc;
    @MockitoBean LeadService leadService;

    @Test
    void acceptsTypedVersionedTransitionAndReturnsCompleteVersionedLead() throws Exception {
        when(leadService.changeStatus(ID, LeadStatus.CONTACTED, 3L)).thenReturn(response(4L, LeadStatus.CONTACTED));
        mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                        .contentType("application/json")
                        .content("{\"status\":\"CONTACTED\",\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.qualificationScore").value(91));
        verify(leadService).changeStatus(ID, LeadStatus.CONTACTED, 3L);
    }

    @Test
    void rejectsMissingStatusAndVersionWithStructuredValidation() throws Exception {
        for (String json : new String[]{"{\"version\":3}", "{\"status\":\"CONTACTED\"}"}) {
            mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                            .contentType("application/json").content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"));
        }
        verifyNoInteractions(leadService);
    }

    @Test
    void rejectsUnknownTargetSafely() throws Exception {
        mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                        .contentType("application/json")
                        .content("{\"status\":\"REOPENED\",\"version\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request body contains an invalid value"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void returnsStructuredConflictForUnsupportedAndStaleTransitions() throws Exception {
        when(leadService.changeStatus(ID, LeadStatus.WON, 3L))
                .thenThrow(new ConflictException("Lead cannot transition from LOST to WON"));
        mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                        .contentType("application/json")
                        .content("{\"status\":\"WON\",\"version\":3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Lead cannot transition from LOST to WON"));

        when(leadService.changeStatus(ID, LeadStatus.CONTACTED, 2L))
                .thenThrow(new ConflictException("Lead changed elsewhere. Refresh and try again"));
        mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                        .contentType("application/json")
                        .content("{\"status\":\"CONTACTED\",\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Lead changed elsewhere. Refresh and try again"));
    }

    @Test
    void returnsStructuredNotFoundForUnknownLead() throws Exception {
        when(leadService.changeStatus(ID, LeadStatus.CONTACTED, 0L))
                .thenThrow(new NotFoundException("Lead not found: " + ID));
        mockMvc.perform(patch("/api/v1/leads/{id}/status", ID)
                        .contentType("application/json")
                        .content("{\"status\":\"CONTACTED\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private LeadResponse response(long version, LeadStatus status) {
        Instant now = Instant.parse("2026-08-13T08:00:00Z");
        return new LeadResponse(ID, version, "Lifecycle Lead", "lifecycle@example.com", "+1 555 0100",
                "Lifecycle Co", "Sales workflow", new BigDecimal("5000.00"), LocalDate.parse("2026-09-01"),
                "A detailed lifecycle request.", "test", status,
                com.mohammadmurrar.leadflow.lead.LeadPriority.HIGH, 91, "Enterprise",
                "Original AI summary", "Original recommended reply", now, now);
    }
}
