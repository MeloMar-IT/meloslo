package com.example.meloslo;

import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class UserFunctionalTest extends BaseFunctionalTest {

    private Map<String, Object> body(String username, String email, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("username", username);
        m.put("email", email);
        m.put("departments", "QA");
        if (password != null) {
            m.put("password", password);
        }
        return m;
    }

    @Test
    void shouldGetCurrentUserInfo() {
        ResponseEntity<User> response = userAuth().getForEntity("/api/v1/users/me", User.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo("user");
    }

    @Test
    void shouldCreateAndListUsers() {
        ResponseEntity<User> createResponse = adminAuth().postForEntity("/api/v1/users",
                body("tester", "tester@example.com", "testpass1"), User.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody().getRole()).isEqualTo(Role.USER);

        ResponseEntity<User[]> listResponse = adminAuth().getForEntity("/api/v1/users", User[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).extracting(User::getUsername).contains("tester");
    }

    @Test
    void shouldUpdateUser() {
        User user = userRepository.findByUsername("user").orElseThrow();

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body("user", "updated@example.com", null));
        ResponseEntity<User> response = adminAuth().exchange("/api/v1/users/" + user.getId(), HttpMethod.PUT, request, User.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEmail()).isEqualTo("updated@example.com");
        assertThat(response.getBody().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldDeleteUser() {
        User user = userRepository.save(new User("temp", "pass", "temp@example.com", "None"));

        adminAuth().delete("/api/v1/users/" + user.getId());

        ResponseEntity<User[]> listResponse = adminAuth().getForEntity("/api/v1/users", User[].class);
        assertThat(listResponse.getBody()).extracting(User::getUsername).doesNotContain("temp");
    }

    @Test
    void regularUserGets403OnAllUserManagementEndpoints() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String adminHash = admin.getPassword();

        assertThat(userAuth().getForEntity("/api/v1/users", String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userAuth().postForEntity("/api/v1/users", body("x", "x@example.com", "password1"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body("admin", "a@example.com", "newpassword1"));
        assertThat(userAuth().exchange("/api/v1/users/" + admin.getId(), HttpMethod.PUT, req, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userAuth().exchange("/api/v1/users/" + admin.getId(), HttpMethod.DELETE, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(userRepository.findByUsername("admin").orElseThrow().getPassword()).isEqualTo(adminHash);
    }

    @Test
    void adminGets404ForUnknownIdAndValidationErrorsGive400() {
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body("ghost", "g@example.com", null));
        assertThat(adminAuth().exchange("/api/v1/users/99999", HttpMethod.PUT, req, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(adminAuth().exchange("/api/v1/users/99999", HttpMethod.DELETE, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(adminAuth().postForEntity("/api/v1/users", Map.of("username", ""), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(adminAuth().postForEntity("/api/v1/users", body("dup", "d@example.com", "password1"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminAuth().postForEntity("/api/v1/users", body("dup", "d@example.com", "password1"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void departmentsAt255CharactersAreStoredAnd256Rejected() {
        String d255 = "d".repeat(255);
        Map<String, Object> ok = body("longdept", "l@example.com", "password1");
        ok.put("departments", d255);
        assertThat(adminAuth().postForEntity("/api/v1/users", ok, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByUsername("longdept").orElseThrow().getDepartments()).hasSize(255);

        Map<String, Object> bad = body("longdept2", "l2@example.com", "password1");
        bad.put("departments", "d".repeat(256));
        assertThat(adminAuth().postForEntity("/api/v1/users", bad, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
