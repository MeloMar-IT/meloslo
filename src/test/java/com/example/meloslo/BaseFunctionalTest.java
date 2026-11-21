package com.example.meloslo;

import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseFunctionalTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void setupBase() {
        userRepository.deleteAll();
        // Create a default admin user for tests
        User admin = new User("admin", passwordEncoder.encode("admin"), "admin@example.com", "IT");
        userRepository.save(admin);
        
        // Create a default regular user
        User user = new User("user", passwordEncoder.encode("password"), "user@example.com", "HR");
        userRepository.save(user);

        // Create testuser for SecurityIntegrationTest
        User testuser = new User("testuser", passwordEncoder.encode("testuser"), "testuser@finance.com", "Finance,Engineering");
        userRepository.save(testuser);
    }

    protected TestRestTemplate adminAuth() {
        return restTemplate.withBasicAuth("admin", "admin");
    }

    protected TestRestTemplate userAuth() {
        return restTemplate.withBasicAuth("user", "password");
    }
}
