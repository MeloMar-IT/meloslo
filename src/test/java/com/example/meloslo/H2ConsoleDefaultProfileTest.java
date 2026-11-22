package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:h2defaulttest")
class H2ConsoleDefaultProfileTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void anonymousRequestToConsoleIsNotFound() {
        assertThat(restTemplate.getForEntity("/h2-console", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/h2-console/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminRequestToConsoleIsNotFound() {
        // admin/admin is seeded in test mode
        ResponseEntity<String> response = restTemplate.withBasicAuth("admin", "admin")
                .getForEntity("/h2-console/", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
