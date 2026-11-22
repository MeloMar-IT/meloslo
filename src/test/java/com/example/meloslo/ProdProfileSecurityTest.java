package com.example.meloslo;

import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:prodprofiletest")
@ActiveProfiles("prod")
class ProdProfileSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void consoleIsNotFoundForAnonymous() {
        assertThat(restTemplate.getForEntity("/h2-console", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/h2-console/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void liveStartCreatesNoTestuserAndNoDefaultAdminPassword() {
        assertThat(userRepository.findByUsername("testuser")).isEmpty();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(passwordEncoder.matches("admin", admin.getPassword())).isFalse();
    }

    @Test
    void defaultAdminCredentialsAreRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin", "admin");
        headers.add("X-Requested-With", "XMLHttpRequest");

        var response = restTemplate.exchange("/api/v1/users/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
