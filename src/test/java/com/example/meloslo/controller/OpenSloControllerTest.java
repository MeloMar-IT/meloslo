package com.example.meloslo.controller;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
public class OpenSloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OpenSloRepository repository;

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        metricRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void shouldCreateAndRetrieveOpenSlo() throws Exception {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "{}");

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("TestSLO"));

        mockMvc.perform(get("/api/v1/openslo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("TestSLO"));
    }

    @Test
    void shouldUpdateOpenSlo() throws Exception {
        OpenSlo slo = repository.save(new OpenSlo("openslo/v1", "SLO", "OldName", "Old Name", "{}"));
        
        OpenSlo updatedSlo = new OpenSlo("openslo/v1", "SLO", "NewName", "New Name", "{\"updated\": true}");

        mockMvc.perform(put("/api/v1/openslo/" + slo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedSlo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("NewName"))
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.spec").value("{\"updated\": true}"));
    }

    @Test
    void shouldDeleteOpenSlo() throws Exception {
        OpenSlo slo = repository.save(new OpenSlo("openslo/v1", "SLO", "ToDelete", "To Delete", "{}"));

        mockMvc.perform(delete("/api/v1/openslo/" + slo.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/openslo/" + slo.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetSliReport() throws Exception {
        OpenSlo sli = repository.save(new OpenSlo("openslo/v1", "SLI", "SliReportTest", "SLI Report Test", "{}"));
        metricRepository.save(new SliMetric(java.time.LocalDateTime.now(), 0.98, sli, null));

        mockMvc.perform(get("/api/v1/openslo/" + sli.getId() + "/sli-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SliReportTest"))
                .andExpect(jsonPath("$.currentValue").value(0.98))
                .andExpect(jsonPath("$.recentMetrics", hasSize(1)));
    }
}
