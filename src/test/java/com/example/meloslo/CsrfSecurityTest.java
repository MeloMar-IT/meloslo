package com.example.meloslo;

import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSRF protection itself. Deliberately does not import {@link CsrfTestConfig}. */
@SpringBootTest
@AutoConfigureMockMvc
class CsrfSecurityTest {

    private static final String BODY = "{\"apiVersion\":\"openslo/v1\",\"kind\":\"SLI\",\"name\":\"%s\","
            + "\"displayName\":\"Csrf SLI\",\"spec\":\"{}\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void users() {
        userRepository.deleteAll();
        userRepository.save(new User("admin", passwordEncoder.encode("admin"), "admin@meloslo.com", null, Role.ADMIN));
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/login").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin").param("password", "admin"))
                .andExpect(status().isOk());
        return session;
    }

    @Test
    void sessionPostWithoutTokenIsForbidden() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/openslo").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("csrf-none")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionPostWithWrongPasswordBasicHeaderStillNeedsAToken() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/openslo").session(session)
                .with(httpBasic("admin", "wrong-password"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("csrf-basic")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionPostWithTokenIsCreated() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/v1/openslo").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("csrf-ok")))
                .andExpect(status().isCreated());
    }

    @Test
    void loginAndLogoutNeedATokenEvenWithBasicHeader() throws Exception {
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin").param("password", "admin"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/login").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin").param("password", "admin"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/logout")).andExpect(status().isForbidden());
    }

    @Test
    void loginAndLogoutEachSetAFreshTokenCookie() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult login = mockMvc.perform(post("/api/login").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin").param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        String loginToken = lastToken(login);
        assertThat(loginToken).isNotBlank();

        MvcResult logout = mockMvc.perform(post("/api/logout").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        // The logout handler first clears the old cookie; the last Set-Cookie is the one a browser keeps
        String logoutToken = lastToken(logout);
        assertThat(logoutToken).isNotBlank().isNotEqualTo(loginToken);
    }

    private static String lastToken(MvcResult result) {
        String value = null;
        for (jakarta.servlet.http.Cookie c : result.getResponse().getCookies()) {
            if ("XSRF-TOKEN".equals(c.getName())) value = c.getValue();
        }
        return value;
    }

    @Test
    void anyRequestReceivesATokenCookie() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void basicAuthPostWithoutSessionNeedsNoToken() throws Exception {
        int statusCode = mockMvc.perform(post("/api/v1/openslo").with(httpBasic("admin", "admin"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("csrf-basic-ok")))
                .andReturn().getResponse().getStatus();

        assertThat(statusCode).isEqualTo(201);
    }
}
