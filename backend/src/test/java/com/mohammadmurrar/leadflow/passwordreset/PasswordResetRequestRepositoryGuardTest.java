package com.mohammadmurrar.leadflow.passwordreset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetRequestRepositoryGuardTest {
    private EntityManager entityManager;
    private TypedQuery<PasswordResetRequest> query;
    private PasswordResetRequestTokenLookup lookup;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(PasswordResetRequest.class)))
                .thenReturn(query);
        when(query.setParameter(eq("tokenHash"), any(byte[].class))).thenReturn(query);
        lookup = new PasswordResetRequestTokenLookupImpl(entityManager);
    }

    @Test
    void rejectsInvalidLengthsBeforeQueryExecution() {
        for (byte[] invalid : new byte[][] {null, new byte[31], new byte[33]}) {
            assertThatThrownBy(() -> lookup.findByTokenHash(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Password reset token hash is invalid");
        }
        verifyNoInteractions(entityManager);
    }

    @Test
    void exactHashUsesAnImmutableQuerySnapshotAndMissingHashReturnsEmpty() {
        when(query.getResultList()).thenReturn(List.of());
        byte[] supplied = new byte[32];
        Arrays.fill(supplied, (byte) 7);

        assertThat(lookup.findByTokenHash(supplied)).isEmpty();
        ArgumentCaptor<byte[]> passed = ArgumentCaptor.forClass(byte[].class);
        verify(query).setParameter(eq("tokenHash"), passed.capture());
        assertThat(passed.getValue()).isNotSameAs(supplied).containsOnly((byte) 7);

        supplied[0] = 99;
        assertThat(passed.getValue()).containsOnly((byte) 7);
    }

    @Test
    void exactMatchingRowIsReturned() {
        PasswordResetRequest matching = mock(PasswordResetRequest.class);
        when(query.getResultList()).thenReturn(List.of(matching));

        assertThat(lookup.findByTokenHash(new byte[32])).contains(matching);
    }

    @Test
    void repositoryApiExposesOnlyTheGuardedByteArrayLookup() {
        List<Method> byteArrayLookups = Arrays.stream(PasswordResetRequestRepository.class.getMethods())
                .filter(method -> method.getParameterCount() == 1)
                .filter(method -> method.getParameterTypes()[0] == byte[].class)
                .toList();

        assertThat(byteArrayLookups).singleElement().satisfies(method -> {
            assertThat(method.getName()).isEqualTo("findByTokenHash");
            assertThat(method.getDeclaringClass()).isEqualTo(PasswordResetRequestTokenLookup.class);
        });
        assertThat(Arrays.stream(PasswordResetRequestRepository.class.getMethods())
                .map(Method::getName)).doesNotContain("findCandidateByTokenHash");
        assertThat(Arrays.stream(PasswordResetRequestTokenLookup.class.getMethods())
                .map(Method::getName)).containsExactly("findByTokenHash");
    }
}
