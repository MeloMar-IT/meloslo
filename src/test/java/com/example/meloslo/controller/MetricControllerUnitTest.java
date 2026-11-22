package com.example.meloslo.controller;

import com.example.meloslo.config.SecurityConfig;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.service.InvalidSliValueException;
import com.example.meloslo.service.OpenSloService;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MetricController.class)
@Import(SecurityConfig.class)
class MetricControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenSloService service;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldGetAllMetricsWhenAdmin() throws Exception {
        when(service.getAllMetrics()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyAllMetricsWhenUser() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUnauthorizedWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateMetricWhenAdmin() throws Exception {
        SliMetric metric = new SliMetric();
        when(service.createMetric(any())).thenReturn(metric);

        mockMvc.perform(post("/api/v1/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 95.0}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturnBadRequestWhenMetricValueIsInvalid() throws Exception {
        when(service.createMetric(any())).thenThrow(new InvalidSliValueException("SLI metric value must be between 0 and 100, got 150.0"));

        mockMvc.perform(post("/api/v1/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 150.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SLI metric value must be between 0 and 100, got 150.0"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldDenyCreateMetricWhenUser() throws Exception {
        mockMvc.perform(post("/api/v1/metrics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 95.0}"))
                .andExpect(status().isForbidden());
    }
}
