package com.mohammadmurrar.leadflow.service;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.service.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceOfferingServiceTest {
    @Mock ServiceOfferingRepository repository;

    @Test
    void createsTrimmedNormalizedActiveService() {
        when(repository.existsByNormalizedName("ai automation")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            ServiceOffering offering = invocation.getArgument(0);
            ReflectionTestUtils.setField(offering, "createdAt", Instant.parse("2026-08-14T00:00:00Z"));
            ReflectionTestUtils.setField(offering, "updatedAt", Instant.parse("2026-08-14T00:00:00Z"));
            return offering;
        });
        ServiceResponse result = service().create(new CreateServiceRequest("  AI   Automation  ", "  Helpful service  "));
        assertThat(result.name()).isEqualTo("AI Automation");
        assertThat(result.description()).isEqualTo("Helpful service");
        assertThat(result.active()).isTrue();
        verify(repository).existsByNormalizedName("ai automation");
    }

    @Test
    void rejectsFriendlyDuplicateAndStaleUpdate() {
        when(repository.existsByNormalizedName("ai automation")).thenReturn(true);
        assertThatThrownBy(() -> service().create(new CreateServiceRequest("AI Automation", null)))
                .isInstanceOf(ConflictException.class).hasMessage("A service with this name already exists");

        ServiceOffering offering = offering();
        when(repository.findById(offering.getId())).thenReturn(Optional.of(offering));
        assertThatThrownBy(() -> service().update(offering.getId(),
                new UpdateServiceRequest(10, "Renamed", null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("changed elsewhere");
    }

    @Test
    void lifecycleIsIdempotentWithoutFlushWhenAlreadySatisfied() {
        ServiceOffering offering = offering();
        when(repository.findById(offering.getId())).thenReturn(Optional.of(offering));
        service().reactivate(offering.getId(), 99);
        verify(repository, never()).flush();

        offering.deactivate();
        service().deactivate(offering.getId(), 99);
        verify(repository, never()).flush();
    }

    @Test
    void activeOptionsUseBoundedDeterministicOrderingAndDatabaseFiltering() {
        PageRequest supplied = PageRequest.of(1, 50);
        when(repository.findActiveOptions(eq("automation"), any(Pageable.class))).thenReturn(Page.empty());
        service().findActiveOptions(" Automation ", supplied);
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findActiveOptions(eq("automation"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isOne();
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    private ServiceOfferingService service() { return new ServiceOfferingService(repository); }
    private ServiceOffering offering() {
        ServiceOffering offering = ServiceOffering.create("AI Automation", null);
        ReflectionTestUtils.setField(offering, "createdAt", Instant.parse("2026-08-14T00:00:00Z"));
        ReflectionTestUtils.setField(offering, "updatedAt", Instant.parse("2026-08-14T00:00:00Z"));
        return offering;
    }
}
