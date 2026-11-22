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

    private static String sloSpec(String objectives) {
        return "spec:\n  objectives:\n" + objectives;
    }

    private void expectCreateStatus(String objectives, int status) throws Exception {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "TargetSLO-" + System.nanoTime(), "Target SLO", sloSpec(objectives));
        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().is(status));
    }

    private void expectUpdateStatus(String objectives, int status) throws Exception {
        OpenSlo existing = repository.save(new OpenSlo("openslo/v1", "SLO", "Existing-" + System.nanoTime(), "Existing", "{}"));
        OpenSlo update = new OpenSlo("openslo/v1", "SLO", existing.getName(), "Existing", sloSpec(objectives));
        mockMvc.perform(put("/api/v1/openslo/" + existing.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().is(status));
    }

    @Test
    void shouldRefuseInvalidTargetsOnPostAndPut() throws Exception {
        String[] invalid = {
                "    - target: 99.9\n",
                "    - target: 1\n",
                "    - target: 0\n",
                "    - targetPercent: 100\n",
                "    - targetPercent: 0\n",
                "    - target: abc\n",
                "    - target: 0.99\n      targetPercent: 99\n",
                "    - target: 0.99\n    - target: 99.9\n",
                "    - target: 0.99\n      ratioMetrics:\n        target: 1.5\n"
        };
        for (String objectives : invalid) {
            expectCreateStatus(objectives, 400);
            expectUpdateStatus(objectives, 400);
        }
    }

    @Test
    void shouldReturnMessageForInvalidTarget() throws Exception {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "MsgSLO", "Msg SLO", sloSpec("    - target: 99.9\n"));
        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldAcceptValidTargetsOnPostAndPut() throws Exception {
        expectCreateStatus("    - target: 0.999\n", 201);
        expectCreateStatus("    - targetPercent: 99.9\n", 201);
        expectUpdateStatus("    - targetPercent: 99.9\n", 200);
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
        metricRepository.save(new SliMetric(java.time.LocalDateTime.now(), 98.0, sli, null));

        mockMvc.perform(get("/api/v1/openslo/" + sli.getId() + "/sli-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SliReportTest"))
                .andExpect(jsonPath("$.currentValue").value(98.0))
                .andExpect(jsonPath("$.recentMetrics", hasSize(1)));
    }
}
