package com.mohammadmurrar.leadflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestService;
import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadSubmissionResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CsrfCookieContractApiTest {
    private static final String HEADER = "X-XSRF-TOKEN";

    @Autowired ObjectMapper objectMapper;
    @Autowired WebApplicationContext context;
    @MockitoBean PublicInquiryService publicInquiry;
    @MockitoBean PasswordResetRequestService passwordResetRequests;
    @MockitoBean PasswordResetConfirmationService passwordResetConfirmations;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void csrfEndpointPrimesReadableRawCookieAndReturnsEncodedRepresentation() throws Exception {
        CsrfPair pair = obtainCsrfPair();

        assertThat(pair.rawToken()).isNotBlank().isNotEqualTo(pair.responseToken());
        assertThat(pair.cookie().isHttpOnly()).isFalse();
        assertThat(pair.cookie().getSecure()).isFalse();
        assertThat(pair.cookie().getPath()).isEqualTo("/");
    }

    @Test
    void rawCookieAndHeaderPairPassesCsrfForEveryAnonymousMutation() throws Exception {
        when(publicInquiry.submit(any())).thenReturn(PublicLeadSubmissionResponse.received());

        CsrfPair inquiryCsrf = obtainCsrfPair();
        mvc.perform(post("/api/v1/public/leads").with(anonymous())
                        .cookie(inquiryCsrf.cookie()).header(HEADER, inquiryCsrf.rawToken())
                        .contentType(MediaType.APPLICATION_JSON).content(inquiryBody()))
                .andExpect(status().isAccepted());

        CsrfPair requestCsrf = obtainCsrfPair();
        mvc.perform(post("/api/v1/auth/password-reset/request").with(anonymous())
                        .cookie(requestCsrf.cookie()).header(HEADER, requestCsrf.rawToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.invalid\"}"))
                .andExpect(status().isNoContent());

        CsrfPair confirmationCsrf = obtainCsrfPair();
        mvc.perform(post("/api/v1/auth/password-reset/confirm").with(anonymous())
                        .cookie(confirmationCsrf.cookie()).header(HEADER, confirmationCsrf.rawToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + "A".repeat(43)
                                + "\",\"newPassword\":\"New-password-42!\"}"))
                .andExpect(status().isNoContent());

        verify(publicInquiry).submit(any());
        verify(passwordResetRequests).request(any(), any());
        verify(passwordResetConfirmations).confirm("A".repeat(43), "New-password-42!");
    }

    @Test
    void missingAndEncodedResponseHeadersAreRejectedWithoutTokenDisclosure(
            CapturedOutput output) throws Exception {
        CsrfPair pair = obtainCsrfPair();

        MvcResult missing = mvc.perform(post("/api/v1/public/leads").with(anonymous())
                        .cookie(pair.cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(inquiryBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(content().string(not(containsString(pair.rawToken()))))
                .andExpect(content().string(not(containsString(pair.responseToken()))))
                .andReturn();

        MvcResult encoded = mvc.perform(post("/api/v1/public/leads").with(anonymous())
                        .cookie(pair.cookie()).header(HEADER, pair.responseToken())
                        .contentType(MediaType.APPLICATION_JSON).content(inquiryBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"))
                .andExpect(content().string(not(containsString(pair.rawToken()))))
                .andExpect(content().string(not(containsString(pair.responseToken()))))
                .andReturn();

        assertThat(missing.getResponse().getContentAsString()).doesNotContain("Csrf");
        assertThat(encoded.getResponse().getContentAsString()).doesNotContain("Csrf");
        assertThat(output).doesNotContain(pair.rawToken(), pair.responseToken());
        verifyNoInteractions(publicInquiry);
    }

    @Test
    void neighboringAdministrativeRoutesRemainAuthenticationProtected() throws Exception {
        CsrfPair pair = obtainCsrfPair();

        mvc.perform(get("/api/v1/dashboard/stats").with(anonymous())
                        .cookie(pair.cookie()).header(HEADER, pair.rawToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    private CsrfPair obtainCsrfPair() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value(HEADER))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        String responseToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        return new CsrfPair(csrfCookie, csrfCookie.getValue(), responseToken);
    }

    private String inquiryBody() {
        return """
                {"fullName":"Synthetic Inquiry","email":"synthetic@example.invalid",
                 "serviceId":"11111111-1111-4111-8111-111111111111",
                 "message":"A sufficiently detailed synthetic inquiry message.","website":""}
                """;
    }

    private record CsrfPair(Cookie cookie, String rawToken, String responseToken) {}
}
