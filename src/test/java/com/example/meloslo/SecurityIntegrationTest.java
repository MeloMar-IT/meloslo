package com.example.meloslo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.example.meloslo.repository.UserRepository userRepository;

    @Autowired
    private com.example.meloslo.repository.OpenSloRepository openSloRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        // Ensure test users exist
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(new com.example.meloslo.model.User("admin", passwordEncoder.encode("admin"), "admin@meloslo.com", null));
        }
        if (userRepository.findByUsername("testuser").isEmpty()) {
            userRepository.save(new com.example.meloslo.model.User("testuser", passwordEncoder.encode("testuser"), "testuser@finance.com", "Finance,Engineering"));
        }

        // Ensure enough SLOs exist for the tests (admin expects >= 4, testuser expects 4)
        long sloCount = openSloRepository.count();
        if (sloCount < 4) {
            for (int i = 0; i < 5; i++) {
                String name = "test-slo-" + i;
                if (openSloRepository.findByName(name).isEmpty()) {
                    com.example.meloslo.model.OpenSlo slo = new com.example.meloslo.model.OpenSlo("openslo/v1", "SLO", name, "SLO " + i, "{}");
                    if (i < 4) {
                        // testuser sees Finance and Engineering
                        slo.setDepartment(i % 2 == 0 ? "Finance" : "Engineering");
                    } else {
                        slo.setDepartment("Other");
                    }
                    openSloRepository.save(slo);
                }
            }
        }
        
        // Ensure enough SLIs exist for testuser (expects 3)
        if (openSloRepository.findByKind("SLI").size() < 3) {
            for (int i = 0; i < 3; i++) {
                String name = "test-sli-" + i;
                if (openSloRepository.findByName(name).isEmpty()) {
                    com.example.meloslo.model.OpenSlo sli = new com.example.meloslo.model.OpenSlo("openslo/v1", "SLI", name, "SLI " + i, "{}");
                    sli.setDepartment("Finance");
                    openSloRepository.save(sli);
                }
            }
        }
    }

    @Test
    void shouldMaintainSessionAfterLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Login
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 2. Access /me
        mockMvc.perform(get("/api/v1/users/me")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void adminUserShouldHaveAdminRole() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Login as admin
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk());

        // 2. Access restricted /api/v1/database/tables
        mockMvc.perform(get("/api/v1/database/tables")
                .session(session))
                .andExpect(status().isOk());
    }

    @Test
    void testUserShouldNotHaveAdminRole() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // 1. Login as testuser
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testuser")
                .param("password", "testuser"))
                .andExpect(status().isOk());

        // 2. Access restricted /api/v1/database/tables should be forbidden (403)
        mockMvc.perform(get("/api/v1/database/tables")
                .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUserShouldSeeAllRecords() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // 1. Login as admin
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk());

        // 2. Access all SLOs as admin
        mockMvc.perform(get("/api/v1/openslo?kind=SLO")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void testUserShouldOnlySeeDepartmentRecords() throws Exception {
        MockHttpSession session = new MockHttpSession();
        // 1. Login as testuser
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testuser")
                .param("password", "testuser"))
                .andExpect(status().isOk());

        // 2. Access all SLOs as testuser (Finance, Engineering)
        mockMvc.perform(get("/api/v1/openslo?kind=SLO")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        
        // 3. Access all SLIs as testuser
        mockMvc.perform(get("/api/v1/openslo?kind=SLI")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void shouldLoginSuccessfullyAndRetrieveUserInfo() throws Exception {
        // 1. Login
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.username").value("admin"));

        // 2. Access /me (MockMvc sessions are usually handled automatically if we use the same mockMvc instance, 
        // but we might need to manually handle it if it doesn't work. Actually, usually we need to use a session)
    }

    @Test
    void shouldFailLoginWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "wrongpassword"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void shouldTestMultipartFormDataLogin() throws Exception {
        // This test will help verify if multipart/form-data works for login
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .param("username", "admin")
                .param("password", "admin"))
                .andExpect(status().isOk());
    }
}
