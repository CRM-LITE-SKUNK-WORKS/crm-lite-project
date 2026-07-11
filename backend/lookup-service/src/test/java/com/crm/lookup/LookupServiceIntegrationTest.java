package com.crm.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the central catalog owner end-to-end: Flyway creates and seeds gnl_st /
 * gnl_tp with the CONTRACT ids (ADR-002), and the read API serves them.
 * Requires Docker. Rerun with: mvn -pl backend/lookup-service test
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LookupServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    @Test
    @DisplayName("contract entries are seeded with their immutable IDs")
    void contractEntriesSeeded() {
        assertThat(fetch("/api/lookups/statuses/ACTV"))
                .containsEntry("id", 1).containsEntry("statusDomain", "GENERAL");
        assertThat(fetch("/api/lookups/statuses/PASV"))
                .containsEntry("id", 2);
        assertThat(fetch("/api/lookups/types/MALE"))
                .containsEntry("id", 1).containsEntry("typeDomain", "GENDER");
        assertThat(fetch("/api/lookups/types/FEMALE"))
                .containsEntry("id", 2);
        assertThat(fetch("/api/lookups/types/INDV"))
                .containsEntry("id", 3).containsEntry("typeDomain", "PARTY_TYPE");
    }

    @Test
    @DisplayName("domain filter returns only that domain's entries")
    void domainFilterWorks() {
        ResponseEntity<List> response = http.get().uri("/api/lookups/types?domain=GENDER")
                .retrieve().toEntity(List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2); // MALE, FEMALE
    }

    @Test
    @DisplayName("unknown code -> clean 404 with messageKey")
    void unknownCodeReturns404() {
        ResponseEntity<Map> response = http.get().uri("/api/lookups/statuses/NOPE")
                .retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("messageKey")).isEqualTo("MSG-LOOKUP-NOT-FOUND");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(String path) {
        ResponseEntity<Map> response = http.get().uri(path).retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
