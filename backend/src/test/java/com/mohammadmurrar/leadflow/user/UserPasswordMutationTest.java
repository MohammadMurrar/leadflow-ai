package com.mohammadmurrar.leadflow.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UserPasswordMutationTest {
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void acceptsOnlyProductionEncodedHashAndKeepsRepresentationRedacted() {
        User user = User.createAdministrator(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "admin@example.invalid", "Admin", "{test}old-hash");
        String plaintextSentinel = "distinctive-plaintext-password";
        String encoded = passwordEncoder.encode(plaintextSentinel);
        assertThat(user.changePasswordHash(encoded)).isTrue();
        assertThat(user.getPasswordHash()).isEqualTo(encoded);
        assertThat(user.changePasswordHash(encoded)).isFalse();
        assertThat(user.toString()).isEqualTo("User[redacted]").doesNotContain(plaintextSentinel);
        for (String invalid : new String[] {null, "", " ", plaintextSentinel,
                "argon2@SpringSecurity_v5_8$argon2id$malformed",
                "{bcrypt}$2a$10$malformed", "{argon2@SpringSecurity_v5_8}replacement-hash",
                "{argon2@SpringSecurity_v5_8}$argon2id$v=19$m=16384,t=2,p=1$bad\rvalue$bad",
                "x".repeat(256)}) {
            assertThatThrownBy(() -> user.changePasswordHash(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Encoded password hash is invalid");
        }
    }
}
