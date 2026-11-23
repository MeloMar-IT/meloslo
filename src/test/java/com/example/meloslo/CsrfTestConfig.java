package com.example.meloslo;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * CSRF protection is on, so tests that exercise controller behaviour send a valid token with every
 * request. Tests that check the CSRF protection itself do not import this configuration.
 */
@TestConfiguration
public class CsrfTestConfig {

    @Bean
    MockMvcBuilderCustomizer csrfForAllRequests() {
        return builder -> builder.defaultRequest(get("/").with(csrf()));
    }
}
