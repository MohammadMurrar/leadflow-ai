package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.email.EmailOutboxRepository;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestRepository;
import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=true",
        "leadflow.password-reset.active-key-version=v1",
        "leadflow.password-reset.hmac-keys=v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
@AutoConfigureMockMvc
class PasswordResetSuppressionApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired EmailOutboxRepository outboxes;
    @MockitoBean UserRepository users;

    @Test
    void nonAdminAccountIsSuppressedThroughTheRealHttpAndOrchestrationPath() throws Exception {
        UUID id = UUID.randomUUID();
        User nonAdmin = mock(User.class);
        when(nonAdmin.getId()).thenReturn(id);
        when(nonAdmin.isEnabled()).thenReturn(true);
        when(nonAdmin.getRole()).thenReturn(null);
        when(nonAdmin.getNormalizedEmail()).thenReturn("member@example.invalid");
        when(users.findByNormalizedEmail("member@example.invalid"))
                .thenReturn(Optional.of(nonAdmin));
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(nonAdmin));

        assertNoContent("member@example.invalid");
        org.assertj.core.api.Assertions.assertThat(requests.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(outboxes.count()).isZero();
    }

    private void assertNoContent(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
    }
}

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=false",
        "leadflow.password-reset.active-key-version=v1",
        "leadflow.password-reset.hmac-keys=v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
@AutoConfigureMockMvc
class PasswordResetFeatureDisabledApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired EmailOutboxRepository outboxes;
    @MockitoBean UserRepository users;

    @Test
    void disabledFeatureSuppressesBeforeAccountLookup() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.invalid\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(""));
        verifyNoInteractions(users);
        org.assertj.core.api.Assertions.assertThat(requests.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(outboxes.count()).isZero();
    }
}
