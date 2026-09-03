package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.passwordreset.PasswordResetConfirmationService;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetApiTest {
    private static final String TOKEN = "A".repeat(43);
    @Autowired MockMvc mvc;
    @Autowired WebApplicationContext context;
    @MockitoBean PasswordResetRequestService requests;
    @MockitoBean PasswordResetConfirmationService confirmations;
    MockMvc rawMvc;

    @BeforeEach
    void setUp() {
        rawMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void exactAnonymousPostRoutesRequireCsrfAndReturnNoStoreNoContent() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/api/v1/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.invalid\"}"),
                post("/api/v1/auth/password-reset/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody()) }) {
            rawMvc.perform(request.with(anonymous()))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(content().string(""));
        }
        verify(requests).request(eq("admin@example.invalid"), any(Instant.class));
        verify(confirmations).confirm(TOKEN, "New-password-42!");

        rawMvc.perform(post("/api/v1/auth/password-reset/request").with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.invalid\"}"))
                .andExpect(status().isForbidden());
        rawMvc.perform(post("/api/v1/auth/password-reset/confirm").with(anonymous())
                        .with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestCreatedAndSuppressedOutcomesAreByteForByteIdentical() throws Exception {
        when(requests.request(anyString(), any())).thenReturn(PasswordResetRequestService.Result.CREATED,
                PasswordResetRequestService.Result.SUPPRESSED);
        String first = requestReset("eligible@example.invalid");
        String second = requestReset("unknown@example.invalid");
        org.assertj.core.api.Assertions.assertThat(first).isEqualTo(second).isEmpty();
    }

    @Test
    void unsafeBodiesAndMediaTypesReturnSafeResponses() throws Exception {
        for (String body : new String[] {"", "{\"email\":", "{\"email\":42}",
                "{\"email\":\"bad\",\"secret\":\"value\"}"}) {
            mvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(content().string(not(containsString("value"))))
                    .andExpect(content().string(not(containsString("Exception"))));
        }
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.TEXT_PLAIN).content("private@example.invalid"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(""));
    }

    @Test
    void everyTextualEmailShapeUsesTheSameEnumerationSafeResponse() throws Exception {
        for (String email : new String[] {"bad", "bad\\n@example.invalid", "", "   ",
                "x".repeat(255)}) {
            mvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email.replace("\\", "\\\\") + "\"}"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(content().string(""));
        }
    }

    @Test
    void canonicalTokenFormsUseOnlySafeFixedValidation() throws Exception {
        for (String token : new String[] {"", " ".repeat(43), "A".repeat(42),
                "A".repeat(44), "A".repeat(42) + "=", "A".repeat(42) + "+",
                "A".repeat(42) + "/"}) {
            mvc.perform(post("/api/v1/auth/password-reset/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token +
                                    "\",\"newPassword\":\"New-password-42!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE))
                    .andExpect(jsonPath("$.details").isEmpty());
        }
    }

    @Test
    void sensitiveFieldsRejectEveryNonStringJsonShapeWithoutEchoingInput() throws Exception {
        for (String value : new String[] {"null", "42", "true", "[]", "{}"}) {
            assertSafeStructuralError("/api/v1/auth/password-reset/request",
                    "{\"email\":" + value + "}", value);
            assertSafeStructuralError("/api/v1/auth/password-reset/confirm",
                    "{\"token\":" + value + ",\"newPassword\":\"New-password-42!\"}", value);
            assertSafeStructuralError("/api/v1/auth/password-reset/confirm",
                    "{\"token\":\"" + TOKEN + "\",\"newPassword\":" + value + "}", value);
        }
        assertSafeStructuralError("/api/v1/auth/password-reset/request", "{}", null);
        assertSafeStructuralError("/api/v1/auth/password-reset/confirm",
                "{\"newPassword\":\"New-password-42!\"}", null);
        assertSafeStructuralError("/api/v1/auth/password-reset/confirm",
                "{\"token\":\"" + TOKEN + "\"}", null);
    }

    @Test
    void unexpectedFailuresAreGenericAndNotConvertedToSuccess() throws Exception {
        doThrow(new IllegalStateException("sensitive database failure"))
                .when(requests).request(anyString(), any());
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"private@example.invalid\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.message").value("The password reset could not be processed"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("sensitive database failure"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void invalidTokensAndPasswordPolicyUseOnlyFixedSafeErrors() throws Exception {
        doThrow(new PasswordResetConfirmationService.InvalidPasswordResetException())
                .when(confirmations).confirm(anyString(), anyString());
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.message").value(
                        PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString("New-password-42!"))));

        reset(confirmations);
        doThrow(new PasswordResetConfirmationService.InvalidPasswordException(
                "Choose a password you have not used before"))
                .when(confirmations).confirm(anyString(), anyString());
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details[0]").value(
                        "newPassword: Choose a password you have not used before"));
    }

    @Test
    void neighboringAndWrongMethodRoutesAreNotAnonymous() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                get("/api/v1/auth/password-reset/request"),
                put("/api/v1/auth/password-reset/request").with(csrf()),
                patch("/api/v1/auth/password-reset/confirm").with(csrf()),
                delete("/api/v1/auth/password-reset/confirm").with(csrf()),
                post("/api/v1/auth/password-reset").with(csrf()),
                post("/api/v1/auth/password-reset/anything").with(csrf()),
                post("/api/v1/auth/password-reset/request-extra").with(csrf()) }) {
            rawMvc.perform(request.with(anonymous())).andExpect(status().isUnauthorized());
        }
    }

    private String requestReset(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString();
    }

    private String confirmBody() {
        return "{\"token\":\"" + TOKEN + "\",\"newPassword\":\"New-password-42!\"}";
    }

    private void assertSafeStructuralError(String path, String body, String suppliedValue)
            throws Exception {
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("com.mohammadmurrar"))));
        if (suppliedValue != null && suppliedValue.length() > 2) {
            result.andExpect(content().string(not(containsString(suppliedValue))));
        }
    }
}
