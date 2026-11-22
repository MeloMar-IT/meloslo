package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:h2devtest")
@ActiveProfiles("dev")
class H2ConsoleDevProfileTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> get(String user, String password) {
        HttpHeaders headers = new HttpHeaders();
        if (user != null) {
            headers.setBasicAuth(user, password);
        }
        headers.add("X-Requested-With", "XMLHttpRequest");
        return restTemplate.exchange("/h2-console/", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void anonymousIsUnauthorized() {
        assertThat(get(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void regularUserIsForbidden() {
        assertThat(get("testuser", "testuser").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminGetsConsoleWithSameOriginFrameOptions() {
        ResponseEntity<String> response = get("admin", "admin");

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("admin response status was %s", response.getStatusCode()).isTrue();
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }
}
