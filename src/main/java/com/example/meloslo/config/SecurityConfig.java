package com.example.meloslo.config;

import com.example.meloslo.model.Role;
import com.example.meloslo.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
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

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (h2ConsoleEnabled) {
            http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        }

        http
            .csrf(AbstractHttpConfigurer::disable) // Keep disabled for now as it's a stateless-style API
            .authorizeHttpRequests(auth -> {
                // Let error dispatches keep their real status (e.g. 404) instead of becoming 401
                auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                var authRequests = auth
                    .requestMatchers("/","/index.html", "/static/**", "/*.png", "/*.jpg", "/*.jpeg", "/*.gif", "/*.svg", "/*.ico").permitAll();
                
                if (h2ConsoleEnabled) {
                    authRequests.requestMatchers(PathRequest.toH2Console()).hasRole("ADMIN");
                } else {
                    // Nothing is mapped here when the console is off, so the request ends in a 404
                    authRequests.requestMatchers("/h2-console", "/h2-console/**").permitAll();
                }
                
                authRequests
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/me").permitAll()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/password").authenticated()
                    .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/database/**", "/api/v1/metrics/**").hasRole("ADMIN")
                    .anyRequest().hasAnyRole("ADMIN", "USER");
            })
            .sessionManagement(session -> session
                .sessionConcurrency(c -> c
                    .maximumSessions(-1)
                    .sessionRegistry(sessionRegistry())
                    .expiredSessionStrategy(event -> event.getResponse().sendError(401, "Session expired"))))
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
            );

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
                        .authorities(authoritiesFor(u))
                        .build();
                })
                .orElseGet(() -> {
                    log.warn("User not found in database: {}", username);
                    throw new UsernameNotFoundException("User not found: " + username);
                });
        };
    }

    private static List<GrantedAuthority> authoritiesFor(com.example.meloslo.model.User u) {
        if (u.isPasswordChangeRequired()) {
            return List.of(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"));
        }
        // Authorities come from the stored role only, never from the username. Missing role = USER.
        String role = u.getRole() == Role.ADMIN ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
