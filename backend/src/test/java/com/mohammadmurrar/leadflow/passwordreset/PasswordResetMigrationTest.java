package com.mohammadmurrar.leadflow.passwordreset;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class PasswordResetMigrationTest {
    @Test
    void v11ContainsRequiredSafeStructureAndNoSensitiveColumns() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V11__create_password_reset_requests.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        assertThat(sql).contains("create table password_reset_requests", "id binary(16)",
                "token_hash binary(32)", "delivery_nonce binary(32)",
                "delivery_key_version varchar(32)", "unique (user_id, active_slot)",
                "on delete cascade", "idx_password_reset_user_created", "idx_password_reset_expiry",
                "idx_password_reset_cleanup", "password_reset_request_id binary(16)",
                "on delete set null",
                "emailoutboxservice enforces event-specific associations",
                "on delete set null can preserve delivery history",
                "'cancelled'", "'password_reset'");
        assertThat(sql).doesNotContain("raw_token", "plaintext", "new_password", "session_id",
                "ip_address", "user_agent", "provider_response", "exception_text", "smtp_password");
        assertThat(sql).doesNotContain("chk_email_outbox_association_type",
                "template_type = 'password_reset' and lead_id is null",
                "template_type <> 'password_reset' and password_reset_request_id is null");
    }
}
