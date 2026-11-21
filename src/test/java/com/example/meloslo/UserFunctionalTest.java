package com.example.meloslo;

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

    @Test
    void shouldGetCurrentUserInfo() {
        ResponseEntity<User> response = userAuth().getForEntity("/api/v1/users/me", User.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getUsername()).isEqualTo("user");
    }

    @Test
    void shouldCreateAndListUsers() {
        Map<String, String> newUser = new HashMap<>();
        newUser.put("username", "tester");
        newUser.put("password", "testpass");
        newUser.put("email", "tester@example.com");
        newUser.put("departments", "QA");
        
        ResponseEntity<User> createResponse = adminAuth().postForEntity("/api/v1/users", newUser, User.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        ResponseEntity<User[]> listResponse = adminAuth().getForEntity("/api/v1/users", User[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).extracting(User::getUsername).contains("tester");
    }

    @Test
    void shouldUpdateUser() {
        User user = userRepository.findByUsername("user").orElseThrow();
        user.setEmail("updated@example.com");
        
        HttpEntity<User> request = new HttpEntity<>(user);
        ResponseEntity<User> response = adminAuth().exchange("/api/v1/users/" + user.getId(), HttpMethod.PUT, request, User.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getEmail()).isEqualTo("updated@example.com");
    }

    @Test
    void shouldDeleteUser() {
        User user = new User("temp", "pass", "temp@example.com", "None");
        user = userRepository.save(user);
        
        adminAuth().delete("/api/v1/users/" + user.getId());
        
        ResponseEntity<User[]> listResponse = adminAuth().getForEntity("/api/v1/users", User[].class);
        assertThat(listResponse.getBody()).extracting(User::getUsername).doesNotContain("temp");
    }
}
