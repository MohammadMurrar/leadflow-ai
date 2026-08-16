package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.qualification.api.QualificationStartResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationAndQualificationApiTest {
    private static final UUID LEAD_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTIFICATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationService notificationService;
    @MockitoBean LeadService leadService;
    @MockitoBean QualificationAttemptService qualificationAttemptService;

    @Test
    void listsNotifications() throws Exception {
        when(notificationService.findAll(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void returnsUnreadNotificationCount() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"unreadCount\":3}"));
    }

    @Test
    void marksOneNotificationAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIFICATION_ID))
                .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(NOTIFICATION_ID);
    }

    @Test
    void marksAllNotificationsAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead();
    }

    @Test
    void returnsStructuredNotFoundForUnknownNotification() throws Exception {
        doThrow(new NotFoundException("Notification not found: " + NOTIFICATION_ID))
                .when(notificationService).markAsRead(NOTIFICATION_ID);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", NOTIFICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification not found: " + NOTIFICATION_ID))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void removedLeadQualificationRouteIsUnavailable() throws Exception {
        mockMvc.perform(post("/api/v1/leads/{id}/qualification", LEAD_ID)
                        .contentType("application/json")
                        .content(qualificationJson()))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedQualificationRejectsMissingKey() throws Exception {
        mockMvc.perform(post("/api/v1/automation/leads/{id}/qualification", LEAD_ID)
                        .contentType("application/json")
                        .content(qualificationJson()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(leadService);
    }

    @Test
    void protectedQualificationRejectsIncorrectKey() throws Exception {
        mockMvc.perform(post("/api/v1/automation/leads/{id}/qualification", LEAD_ID)
                        .header("X-Automation-Key", "incorrect-test-key")
                        .contentType("application/json")
                        .content(qualificationJson()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(leadService);
    }

    @Test
    void protectedQualificationAcceptsConfiguredKey() throws Exception {
        when(leadService.qualify(eq(LEAD_ID), any())).thenReturn(qualifiedLead());

        mockMvc.perform(post("/api/v1/automation/leads/{id}/qualification", LEAD_ID)
                        .header("X-Automation-Key", "test-key")
                        .contentType("application/json")
                        .content(qualificationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(LEAD_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUALIFIED"));
        verify(leadService).qualify(eq(LEAD_ID), any());
    }

    @Test
    void attemptStartRetainsAutomationAuthentication() throws Exception {
        UUID attemptId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        String body = "{\"workflowExecutionId\":\"test-execution\"}";
        mockMvc.perform(post("/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/start",
                        LEAD_ID, attemptId).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/start",
                        LEAD_ID, attemptId).header("X-Automation-Key", "wrong")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        when(qualificationAttemptService.start(eq(LEAD_ID), eq(attemptId), any()))
                .thenReturn(new QualificationStartResponse(attemptId, QualificationAttemptStatus.PROCESSING, true));
        mockMvc.perform(post("/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/start",
                        LEAD_ID, attemptId).header("X-Automation-Key", "test-key")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    private String qualificationJson() {
        return """
                {
                  "score": 88,
                  "priority": "HIGH",
                  "category": "Backend Development",
                  "summary": "Qualified lead with a defined integration requirement.",
                  "recommendedReply": "Thanks for sharing your requirements. Let us schedule a discovery call."
                }
                """;
    }

    private LeadResponse qualifiedLead() {
        Instant now = Instant.parse("2026-08-12T07:00:00Z");
        return new LeadResponse(LEAD_ID, 0L, "Alex Morgan", "alex@example.com", null,
                "Northstar Services", "Backend API", new BigDecimal("4500.00"),
                LocalDate.parse("2026-09-01"), "A sufficiently detailed lead request.", "test",
                LeadStatus.QUALIFIED, LeadPriority.HIGH, 88, "Backend Development",
                "Qualified lead with a defined integration requirement.",
                "Thanks for sharing your requirements. Let us schedule a discovery call.", now, now);
    }
}
