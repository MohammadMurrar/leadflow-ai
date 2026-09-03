package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxMigrationTest {
    @Test
    void migrationMatchesSafeJpaContractWithoutRedundantIndexesOrContentColumns() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V10__create_email_outbox.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql).contains(
                "id binary(16) not null",
                "version bigint not null default 0",
                "template_type varchar(40) not null",
                "recipient varchar(254)",
                "lead_id binary(16) null",
                "deduplication_key varchar(180)",
                "lease_token varchar(36) null",
                "locked_until timestamp(6) null",
                "on delete set null",
                "unique (deduplication_key)",
                "index idx_email_outbox_pending (status, available_at, created_at)",
                "index idx_email_outbox_lease (status, locked_until)");
        assertThat(sql).doesNotContain("smtp_password", "reset_token", "phone", "inquiry_message",
                "ai_summary", "recommended_reply", "rendered_body", "provider_response",
                "stack_trace", "index idx_email_outbox_deduplication");
    }
}
