package com.example.meloslo.config;

import com.example.meloslo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for simplicity in this exercise
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/static/**", "/h2-console/**").permitAll()
                .requestMatchers("/api/v1/users/me").permitAll() // Allow getting current user info (checks auth)
                .requestMatchers("/api/v1/database/**", "/api/v1/metrics/**").hasRole("ADMIN") // Restrict sensitive tools to ADMIN
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults())
            .formLogin(form -> form
                .loginPage("/") // Redirect to root if not authenticated
                .loginProcessingUrl("/api/login")
                .successHandler((request, response, authentication) -> {
                    log.info("User {} logged in successfully", authentication.getName());
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"success\",\"username\":\"" + authentication.getName() + "\"}");
                    response.getWriter().flush();
                })
                .failureHandler((request, response, exception) -> {
                    log.error("Authentication failed: {}", exception.getMessage());
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"" + exception.getMessage() + "\"}");
                    response.getWriter().flush();
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"success\",\"message\":\"Logged out\"}");
                    response.getWriter().flush();
                })
                .permitAll()
            )
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)); // Allow H2 console

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            log.info("Attempting authentication for user: {}", username);
            return userRepository.findByUsername(username)
                .map(u -> {
                    log.debug("User found in database: {}", username);
                    return User.builder()
                        .username(u.getUsername())
                        .password(u.getPassword())
                        .roles("admin".equals(u.getUsername()) ? "ADMIN" : "USER")
                        .build();
                })
                .orElseGet(() -> {
                    log.warn("User not found in database: {}", username);
                    throw new UsernameNotFoundException("User not found: " + username);
                });
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
