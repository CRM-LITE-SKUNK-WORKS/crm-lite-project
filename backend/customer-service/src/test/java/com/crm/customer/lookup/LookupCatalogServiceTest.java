package com.crm.customer.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LookupCatalogServiceTest {

    private static final LookupStatusResponse ACTV = new LookupStatusResponse(1L, "ACTV", "Active", "GENERAL");
    private static final LookupTypeResponse MALE = new LookupTypeResponse(1L, "MALE", "Male", "GENDER");
    private static final LookupTypeResponse INDV = new LookupTypeResponse(3L, "INDV", "Individual", "PARTY_TYPE");

    private LookupCatalogService service(LookupCatalogClient client, Duration ttl) {
        return new LookupCatalogService(client, new LookupCatalogProperties(null, ttl, 256));
    }

    @Test
    void resolvesStatusIdFromCentralCatalog() {
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchStatus("ACTV")).thenReturn(Optional.of(ACTV));

        long id = service(client, Duration.ofMinutes(15)).resolveStatusId("status", "ACTV", "GENERAL");

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void unknownCode_rejectedAsValidationProblem() {
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchStatus("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(client, Duration.ofMinutes(15)).resolveStatusId("status", "NOPE", "GENERAL"))
                .isInstanceOf(UnknownLookupCodeException.class)
                .satisfies(ex -> assertThat(((UnknownLookupCodeException) ex).getField()).isEqualTo("status"));
    }

    @Test
    void wrongDomain_rejected() {
        // Asking for INDV as a GENDER must fail even though the code exists centrally.
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchType("INDV")).thenReturn(Optional.of(INDV));

        assertThatThrownBy(() -> service(client, Duration.ofMinutes(15)).resolveTypeId("gender", "INDV", "GENDER"))
                .isInstanceOf(UnknownLookupCodeException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("PARTY_TYPE"));
    }

    @Test
    void catalogUnavailable_failsClosed() {
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchType("MALE"))
                .thenThrow(new LookupCatalogUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> service(client, Duration.ofMinutes(15)).resolveTypeId("gender", "MALE", "GENDER"))
                .isInstanceOf(LookupCatalogUnavailableException.class);
    }

    @Test
    void cachedValue_servedWithoutSecondRemoteCall_andSurvivesOutage() {
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchType("MALE"))
                .thenReturn(Optional.of(MALE))
                .thenThrow(new LookupCatalogUnavailableException("down", new RuntimeException()));
        LookupCatalogService service = service(client, Duration.ofMinutes(15));

        assertThat(service.resolveTypeId("gender", "MALE", "GENDER")).isEqualTo(1L);
        // Second resolve hits the cache: no second HTTP call, and the outage the mock
        // would throw on a second call never surfaces.
        assertThat(service.resolveTypeId("gender", "MALE", "GENDER")).isEqualTo(1L);
        verify(client, times(1)).fetchType("MALE");
    }

    @Test
    void expiredCache_refetchesFromCatalog() {
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchType("MALE")).thenReturn(Optional.of(MALE));
        LookupCatalogService service = service(client, Duration.ZERO);

        service.resolveTypeId("gender", "MALE", "GENDER");
        service.resolveTypeId("gender", "MALE", "GENDER");

        verify(client, times(2)).fetchType("MALE");
    }

    @Test
    void renumberedCentralCatalog_refusedLoudly() {
        // ADR-002 contract guard: if the central catalog ever returns ACTV with an id
        // other than the contract-fixed 1, persisting against it would corrupt every
        // locally stored reference — the service must refuse.
        LookupCatalogClient client = mock(LookupCatalogClient.class);
        when(client.fetchStatus("ACTV"))
                .thenReturn(Optional.of(new LookupStatusResponse(42L, "ACTV", "Active", "GENERAL")));

        assertThatThrownBy(() -> service(client, Duration.ofMinutes(15)).resolveStatusId("status", "ACTV", "GENERAL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contract");
    }
}
