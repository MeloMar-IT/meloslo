package com.example.meloslo.controller;

import com.example.meloslo.config.SecurityConfig;
import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, com.example.meloslo.CsrfTestConfig.class})
class UserControllerUnitTest {

    private static final String VALID_CREATE =
            "{\"username\":\"newuser\",\"password\":\"password1\",\"email\":\"new@example.com\",\"departments\":\"QA\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    private User user(long id, String name, Role role) {
        User u = new User(name, "hash", name + "@example.com", null, role);
        u.setId(id);
        return u;
    }

    // ---- /me ----

    @Test
    @WithMockUser(username = "testuser")
    void shouldGetCurrentUserAndNotReturnPassword() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("encodedPassword");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void shouldReturn401WhenGettingCurrentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---- USER is forbidden ----

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void regularUserGets403OnAllManagementEndpointsAndNothingIsWritten() throws Exception {
        String body = "{\"username\":\"bob\",\"email\":\"b@example.com\",\"role\":\"ADMIN\",\"password\":\"password1\"}";

        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // own id as well
        mockMvc.perform(put("/api/v1/users/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/1")).andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).delete(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @WithMockUser(authorities = "PASSWORD_CHANGE_REQUIRED")
    void passwordChangeRequiredUserCannotUseUserManagement() throws Exception {
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", authorities = "PASSWORD_CHANGE_REQUIRED")
    void passwordChangeRequiredUserMayChangeOwnPassword() throws Exception {
        User alice = user(1, "alice", Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("oldpassword", "hash")).thenReturn(true);

        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isNoContent());
    }

    // ---- ADMIN ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesUserWithEncodedPasswordAndDefaultRole() throws Exception {
        when(passwordEncoder.encode("password1")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userRepository).saveAndFlush(argThat(u -> "encoded".equals(u.getPassword()) && u.getRole() == Role.USER));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateAdmin() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"n\",\"password\":\"password1\",\"email\":\"n@example.com\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsInvalidInputWith400() throws Exception {
        String longDepts = "d".repeat(256);
        List<String> bodies = List.of(
                "{\"username\":\"\",\"password\":\"password1\",\"email\":\"a@b.com\"}",
                "{\"password\":\"password1\",\"email\":\"a@b.com\"}",
                "{\"username\":\"u\",\"password\":\"password1\",\"email\":\"not-an-email\"}",
                "{\"username\":\"u\",\"password\":\"password1\",\"email\":\"\"}",
                "{\"username\":\"u\",\"password\":\"short\",\"email\":\"a@b.com\"}",
                "{\"username\":\"u\",\"password\":\"\",\"email\":\"a@b.com\"}",
                "{\"username\":\"u\",\"password\":\"password1\",\"email\":\"a@b.com\",\"departments\":\"" + longDepts + "\"}");
        for (String b : bodies) {
            mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(b))
                    .andExpect(status().isBadRequest());
        }
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDuplicateUsernameGives409() throws Exception {
        when(userRepository.existsByUsername("newuser")).thenReturn(true);
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGives409WhenConstraintTripsOnRace() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAndDeleteUnknownIdGive404() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/v1/users/99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/99")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateIgnoresExtraFieldsAndNullRoleKeepsRole() throws Exception {
        User existing = user(5, "existing", Role.ADMIN);
        User other = user(6, "other", Role.ADMIN);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(existing, other));
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/v1/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":77,\"passwordChangeRequired\":true,\"username\":\"renamed\",\"email\":\"r@example.com\",\"role\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("renamed"));
        verify(userRepository, never()).deleteById(77L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsShortPasswordAndInvalidInput() throws Exception {
        mockMvc.perform(put("/api/v1/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"x@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDuplicateUsernameGives409() throws Exception {
        User existing = user(5, "existing", Role.USER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsernameAndIdNot("taken", 5L)).thenReturn(true);
        mockMvc.perform(put("/api/v1/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\",\"email\":\"x@example.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void demotingOrDeletingLastAdminGives409() throws Exception {
        User last = user(1, "boss", Role.ADMIN);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(last));
        when(userRepository.findById(1L)).thenReturn(Optional.of(last));

        mockMvc.perform(put("/api/v1/users/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"boss\",\"email\":\"b@example.com\",\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/users/1")).andExpect(status().isConflict());
        verify(userRepository, never()).delete(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDeleteUser() throws Exception {
        User u = user(2, "temp", Role.USER);
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(user(1, "boss", Role.ADMIN)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));
        mockMvc.perform(delete("/api/v1/users/2")).andExpect(status().isOk());
        verify(userRepository).delete(u);
    }

    // ---- /me/password ----

    @Test
    void changeOwnPasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void changeOwnPasswordUsesPasswordOnlyUpdate() throws Exception {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2, "bob", Role.USER)));
        when(passwordEncoder.matches("oldpassword", "hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("newhash");

        // extra role / departments / id fields are ignored
        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"newpassword1\","
                                + "\"role\":\"ADMIN\",\"departments\":\"X\",\"id\":1,\"username\":\"admin\"}"))
                .andExpect(status().isNoContent());

        verify(userRepository).updatePassword("bob", "newhash");
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void changeOwnPasswordRejectsWrongCurrentShortNewAndSamePassword() throws Exception {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2, "bob", Role.USER)));
        when(passwordEncoder.matches("wrongpass", "hash")).thenReturn(false);
        when(passwordEncoder.matches("oldpassword", "hash")).thenReturn(true);

        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongpass\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldpassword\",\"newPassword\":\"oldpassword\"}"))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).updatePassword(anyString(), anyString());
    }
}
