package com.example.meloslo.controller;

import com.example.meloslo.config.SecurityConfig;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(username = "testuser")
    void shouldGetCurrentUser() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void shouldReturn401WhenGettingCurrentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldGetAllUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldCreateUser() throws Exception {
        User user = new User();
        user.setUsername("newuser");
        user.setPassword("password");
        
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"newuser\", \"password\": \"password\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldUpdateUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("existinguser");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        mockMvc.perform(put("/api/v1/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"updateduser\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isOk());
    }
}
