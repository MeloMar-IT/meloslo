package com.example.meloslo;

import com.example.meloslo.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(CsrfTestConfig.class)
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
        // Start every test from the same two users (tests rename, demote and add users)
        userRepository.deleteAll();
        userRepository.save(new com.example.meloslo.model.User("admin", passwordEncoder.encode("admin"), "admin@meloslo.com", null, Role.ADMIN));
        userRepository.save(new com.example.meloslo.model.User("testuser", passwordEncoder.encode("testuser"), "testuser@finance.com", "Finance,Engineering", Role.USER));

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

    // ---- stored roles, self-service password, session revocation ----

    private MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/login").session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", username).param("password", password))
                .andExpect(status().isOk());
        return session;
    }

    private com.example.meloslo.model.User saveUser(String name, String password, Role role) {
        return userRepository.save(new com.example.meloslo.model.User(
                name, passwordEncoder.encode(password), name + "@example.com", null, role));
    }

    private static String updateBody(String username, String role) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"role\":\"" + role + "\"}";
    }

    @Test
    void testUserCannotChangeAdminPassword() throws Exception {
        MockHttpSession session = login("testuser", "testuser");
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/" + adminId).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"email\":\"a@example.com\",\"password\":\"hacked-pass1\"}"))
                .andExpect(status().isForbidden());

        login("admin", "admin");
    }

    @Test
    void renamingUserToAdminGrantsNothing() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");
        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        Long testId = userRepository.findByUsername("testuser").orElseThrow().getId();

        // keep one real admin around, rename the old one, then rename testuser to "admin"
        saveUser("second", "secondpass1", Role.ADMIN);
        mockMvc.perform(put("/api/v1/users/" + adminId).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("boss", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/" + testId).session(login("second", "secondpass1"))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("admin", "USER")))
                .andExpect(status().isOk());

        MockHttpSession fake = login("admin", "testuser");
        mockMvc.perform(get("/api/v1/database/tables").session(fake)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").session(fake)).andExpect(status().isForbidden());
    }

    @Test
    void demotedAdminLosesExistingSession() throws Exception {
        MockHttpSession adminSession = login("admin", "admin");
        Long secondId = saveUser("second", "secondpass1", Role.ADMIN).getId();
        MockHttpSession secondSession = login("second", "secondpass1");
        mockMvc.perform(get("/api/v1/users").session(secondSession)).andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/" + secondId).session(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("second", "USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users").session(secondSession)).andExpect(status().isUnauthorized());
        // the editing admin keeps their own session
        mockMvc.perform(get("/api/v1/users").session(adminSession)).andExpect(status().isOk());
    }

    @Test
    void selfRenamedAdminSessionIsRevokedAndLaterDemotionAndDeletionApply() throws Exception {
        saveUser("second", "secondpass1", Role.ADMIN);
        Long selfId = saveUser("selfren", "selfpass12", Role.ADMIN).getId();
        MockHttpSession selfSession = login("selfren", "selfpass12");

        // self-rename: the current session must be expired, not kept with the old principal
        mockMvc.perform(put("/api/v1/users/" + selfId).session(selfSession)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("selfren2", "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").session(selfSession)).andExpect(status().isUnauthorized());

        // log in under the new name, then another admin demotes and deletes that account
        MockHttpSession renamed = login("selfren2", "selfpass12");
        mockMvc.perform(get("/api/v1/users").session(renamed)).andExpect(status().isOk());
        MockHttpSession second = login("second", "secondpass1");
        mockMvc.perform(put("/api/v1/users/" + selfId).session(second)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("selfren2", "USER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").session(renamed)).andExpect(status().isUnauthorized());

        MockHttpSession readmin = login("second", "secondpass1");
        mockMvc.perform(put("/api/v1/users/" + selfId).session(readmin)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody("selfren2", "ADMIN")))
                .andExpect(status().isOk());
        MockHttpSession again = login("selfren2", "selfpass12");
        mockMvc.perform(delete("/api/v1/users/" + selfId).session(readmin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users").session(again)).andExpect(status().isUnauthorized());
    }

    @Test
    void userWithPasswordChangeRequiredIsLimitedToPasswordChange() throws Exception {
        saveUser("temp", "temppass12", Role.ADMIN);
        com.example.meloslo.model.User temp = userRepository.findByUsername("temp").orElseThrow();
        temp.setPasswordChangeRequired(true);
        userRepository.save(temp);

        MockHttpSession session = login("temp", "temppass12");
        mockMvc.perform(get("/api/v1/openslo").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mockMvc.perform(put("/api/v1/users/me/password").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"temppass12\",\"newPassword\":\"changedpass1\"}"))
                .andExpect(status().isNoContent());

        MockHttpSession fresh = login("temp", "changedpass1");
        mockMvc.perform(get("/api/v1/database/tables").session(fresh)).andExpect(status().isOk());
    }

    @Test
    void ownPasswordChangeKeepsRoleAndDepartmentsSetByAdminMeanwhile() throws Exception {
        MockHttpSession session = login("testuser", "testuser");

        // an admin changes departments right before the user's own password change
        com.example.meloslo.model.User t = userRepository.findByUsername("testuser").orElseThrow();
        t.setDepartments("HR");
        userRepository.save(t);

        mockMvc.perform(put("/api/v1/users/me/password").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"testuser\",\"newPassword\":\"brandnew123\",\"role\":\"ADMIN\",\"departments\":\"X\"}"))
                .andExpect(status().isNoContent());

        com.example.meloslo.model.User after = userRepository.findByUsername("testuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Role.USER, after.getRole());
        org.junit.jupiter.api.Assertions.assertEquals("HR", after.getDepartments());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "testuser").param("password", "testuser"))
                .andExpect(status().isUnauthorized());
        login("testuser", "brandnew123");
    }

    @Test
    void wrongCurrentPasswordGives400() throws Exception {
        MockHttpSession session = login("testuser", "testuser");
        mockMvc.perform(put("/api/v1/users/me/password").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"nope\",\"newPassword\":\"brandnew123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentDemotionsLeaveAtLeastOneAdmin() throws Exception {
        // exactly two admins: c1 and c2
        com.example.meloslo.model.User old = userRepository.findByUsername("admin").orElseThrow();
        old.setRole(Role.USER);
        userRepository.save(old);
        Long c1 = saveUser("c1", "password1", Role.ADMIN).getId();
        Long c2 = saveUser("c2", "password1", Role.ADMIN).getId();

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> d1 = () -> demote(start, c1, "c1");
            java.util.concurrent.Callable<Integer> d2 = () -> demote(start, c2, "c2");
            var f1 = pool.submit(d1);
            var f2 = pool.submit(d2);
            start.countDown();
            java.util.List<Integer> codes = new java.util.ArrayList<>(
                    java.util.List.of(f1.get(30, java.util.concurrent.TimeUnit.SECONDS),
                            f2.get(30, java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(codes);
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(200, 409), codes);
        } finally {
            pool.shutdownNow();
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN).count());
    }

    private int demote(java.util.concurrent.CountDownLatch start, Long id, String name) throws Exception {
        start.await();
        return mockMvc.perform(put("/api/v1/users/" + id)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(name).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(updateBody(name, "USER")))
                .andReturn().getResponse().getStatus();
    }
}
