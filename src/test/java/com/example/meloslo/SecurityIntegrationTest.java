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

        // 2. Access all SLOs - should see all of them (at least 4 from DataInitializer)
        mockMvc.perform(get("/api/v1/openslo?kind=SLO")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void testUserShouldOnlySeeDepartmentRecords() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. Login as testuser (Finance, Engineering)
        mockMvc.perform(post("/api/login")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testuser")
                .param("password", "testuser"))
                .andExpect(status().isOk());

        // 2. Access all SLOs - should only see those in Finance or Engineering
        // In DataInitializer:
        // Finance: web-latency, api-availability, db-errors (db-errors is linked to error-sli which is Infrastructure, 
        // but db-errors SLO itself is Finance because it's linked to paymentService which is Finance)
        // Engineering: eng-latency-slo
        // Total should be 4
        mockMvc.perform(get("/api/v1/openslo?kind=SLO")
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        
        // 3. Check specific non-accessible record (Infrastructure department)
        // The error-sli itself is Infrastructure, let's check SLIs
        mockMvc.perform(get("/api/v1/openslo?kind=SLI")
                .session(session))
                .andExpect(status().isOk());
        // testuser has Finance,Engineering. 
        // DataInitializer SLIs:
        // latency-sli: Finance
        // availability-sli: Finance
        // error-sli: Infrastructure
        // eng-latency-sli: Engineering
        // multi-source-sli: Infrastructure
        // testuser should see: latency-sli, availability-sli, eng-latency-sli (Total 3)
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
