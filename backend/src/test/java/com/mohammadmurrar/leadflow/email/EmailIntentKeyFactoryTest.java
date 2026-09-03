package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmailIntentKeyFactoryTest {
    @Test
    void concealedResetKeysAreDeterministicAndHideBothInputs() {
        EmailIntentKeyFactory factory = new EmailIntentKeyFactory();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String one = factory.concealedKey("password-reset", first, " Admin@Example.Invalid ");
        assertThat(factory.concealedKey("password-reset", first, "admin@example.invalid"))
                .isEqualTo(one);
        assertThat(factory.concealedKey("password-reset", second, "admin@example.invalid"))
                .isNotEqualTo(one);
        assertThat(factory.concealedKey("password-reset", first, "other@example.invalid"))
                .isNotEqualTo(one);
        assertThat(one).matches("password-reset:[0-9a-f]{64}")
                .doesNotContain(first.toString(), "admin", "example", "@");
    }

    private final EmailIntentKeyFactory factory = new EmailIntentKeyFactory();

    @Test
    void keysAreDeterministicDistinctGrammarCompatibleAndContainNoRecipient() {
        UUID firstEvent = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID secondEvent = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String recipient = "Distinctive.Person+tag@Example.invalid";
        String normalized = recipient.toLowerCase(java.util.Locale.ROOT);

        String first = factory.key("new-lead", firstEvent, "  " + recipient + "  ");
        assertThat(first).isEqualTo(factory.key("new-lead", firstEvent, normalized));
        assertThat(first).matches("^[a-z][a-z0-9-]{1,39}:[A-Za-z0-9][A-Za-z0-9._:-]{0,138}$")
                .hasSizeLessThanOrEqualTo(180)
                .doesNotContain(recipient, normalized, "Distinctive.Person");
        assertThat(factory.key("new-lead", secondEvent, normalized)).isNotEqualTo(first);
        assertThat(factory.key("qualification-success", firstEvent, normalized)).isNotEqualTo(first);
        assertThat(factory.key("new-lead", firstEvent, "other@example.invalid")).isNotEqualTo(first);
    }
}
