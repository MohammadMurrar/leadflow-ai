package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxRepositoryApiTest {
    @Test
    void publicRepositoryExposesNoRawCreationOrInsertionFragment() {
        assertThat(Arrays.stream(EmailOutboxRepository.class.getMethods())
                .map(Method::getName))
                .doesNotContain("insertIfAbsent");
        assertThat(Arrays.stream(EmailOutboxRepository.class.getInterfaces())
                .map(Class::getSimpleName))
                .doesNotContain("EmailOutboxInsertDao", "EmailOutboxInsertRepository");
        assertThat(Arrays.stream(EmailOutboxRepository.class.getDeclaredMethods()))
                .noneMatch(this::acceptsRawCreationShape)
                .noneMatch(method -> Arrays.asList(method.getParameterTypes())
                        .contains(EmailOutbox.class));

        assertThat(Modifier.isPublic(EmailOutboxInsertDao.class.getModifiers())).isFalse();
        assertThat(Arrays.stream(EmailOutboxInsertDao.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("insertIfAbsent")))
                .isNotEmpty()
                .allMatch(method -> !Modifier.isPublic(method.getModifiers()));
        assertThat(Arrays.stream(EmailOutboxService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().startsWith("enqueue")))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("enqueue", "enqueuePasswordReset");
    }

    private boolean acceptsRawCreationShape(Method method) {
        var parameters = Arrays.asList(method.getParameterTypes());
        return parameters.contains(String.class)
                && parameters.contains(UUID.class)
                && parameters.contains(Instant.class);
    }
}
