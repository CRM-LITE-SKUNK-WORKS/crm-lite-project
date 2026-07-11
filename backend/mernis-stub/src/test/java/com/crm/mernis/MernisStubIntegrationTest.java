package com.crm.mernis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Deterministic stub behaviour (KR-10): valid 11-digit ids verify unless deny-listed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "mernis.stub.denied-ids=99999999999")
class MernisStubIntegrationTest {

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

    private ResponseEntity<Map> verify(String nationalityId) {
        String body = """
                {"nationalityId": "%s", "firstName": "Test", "lastName": "User", "birthDate": "1990-01-01"}
                """.formatted(nationalityId);
        return http.post().uri("/api/mernis/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(Map.class);
    }

    @Test
    @DisplayName("syntactically valid id verifies")
    void validIdVerifies() {
        ResponseEntity<Map> response = verify("12345678901");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("verified")).isEqualTo(true);
    }

    @Test
    @DisplayName("deny-listed id is rejected (stable local 'rejected' fixture)")
    void deniedIdRejected() {
        ResponseEntity<Map> response = verify("99999999999");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("verified")).isEqualTo(false);
    }

    @Test
    @DisplayName("malformed id (not 11 digits) is rejected")
    void malformedIdRejected() {
        ResponseEntity<Map> response = verify("123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("verified")).isEqualTo(false);
    }
}
