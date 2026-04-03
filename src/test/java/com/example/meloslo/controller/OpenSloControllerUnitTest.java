package com.example.meloslo.controller;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.service.OpenSloService;
import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpenSloController.class)
@Import(SecurityConfig.class)
@WithMockUser
public class OpenSloControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenSloService service;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private OpenSlo slo;

    @BeforeEach
    void setUp() {
        slo = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "{}");
        slo.setId(1L);
    }

    @Test
    void shouldGetDashboardStats() throws Exception {
        DashboardStats stats = new DashboardStats(5, 10, 8, 1, 1);
        when(service.getDashboardStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/openslo/dashboard-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").value(5))
                .andExpect(jsonPath("$.healthySlos").value(8));
    }

    @Test
    void shouldGetAllRecords() throws Exception {
        when(service.getAllRecords()).thenReturn(Arrays.asList(slo));

        mockMvc.perform(get("/api/v1/openslo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("TestSLO"));
    }

    @Test
    void shouldGetRecordById() throws Exception {
        when(service.getRecordById(1L)).thenReturn(Optional.of(slo));

        mockMvc.perform(get("/api/v1/openslo/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TestSLO"));
    }

    @Test
    void shouldReturnNotFoundForInvalidId() throws Exception {
        when(service.getRecordById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/openslo/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateRecord() throws Exception {
        when(service.createRecord(any(OpenSlo.class))).thenReturn(slo);

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("TestSLO"));
    }

    @Test
    void shouldUpdateRecord() throws Exception {
        when(service.updateRecord(eq(1L), any(OpenSlo.class))).thenReturn(slo);

        mockMvc.perform(put("/api/v1/openslo/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TestSLO"));
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentRecord() throws Exception {
        when(service.updateRecord(eq(99L), any(OpenSlo.class))).thenThrow(new RuntimeException("Not found"));

        mockMvc.perform(put("/api/v1/openslo/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteRecord() throws Exception {
        mockMvc.perform(delete("/api/v1/openslo/1"))
                .andExpect(status().isNoContent());
    }
}
