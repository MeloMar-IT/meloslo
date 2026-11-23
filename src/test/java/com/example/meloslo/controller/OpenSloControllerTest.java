package com.example.meloslo.controller;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
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
@org.springframework.context.annotation.Import(com.example.meloslo.CsrfTestConfig.class)
@WithMockUser(username = "admin", roles = "ADMIN")
public class OpenSloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OpenSloRepository repository;

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void wipe() {
        jdbc.update("DELETE FROM slo_slis");
        jdbc.update("UPDATE openslo_records SET service_id = NULL, datasource_id = NULL, alerting_source_id = NULL");
        metricRepository.deleteAll();
        repository.deleteAll();
    }

    private OpenSlo saveRecord(String kind, String name) {
        return repository.save(new OpenSlo("openslo/v1", kind, name, name + " display", "{}"));
    }

    private OpenSlo withSlis(OpenSlo slo, OpenSlo... slis) {
        slo.setSlis(new java.util.ArrayList<>(java.util.List.of(slis)));
        return repository.save(slo);
    }

    private String json(OpenSlo r) throws Exception {
        return objectMapper.writeValueAsString(r);
    }

    private String serviceBody(String name, String slosJson) {
        return "{\"apiVersion\":\"openslo/v1\",\"kind\":\"BusinessService\",\"name\":\"" + name
                + "\",\"displayName\":\"" + name + "\",\"spec\":\"{}\"" + (slosJson == null ? "" : ",\"slos\":" + slosJson) + "}";
    }

    @Test
    void deleteOfSliUsedByAnSloIsConflictAndKeepsEverything() throws Exception {
        OpenSlo sli = saveRecord("SLI", "used-sli");
        OpenSlo slo = withSlis(saveRecord("SLO", "using-slo"), sli);
        metricRepository.save(new SliMetric(java.time.LocalDateTime.now(), 90.0, sli, null));

        mockMvc.perform(delete("/api/v1/openslo/" + sli.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("used-sli")));

        org.assertj.core.api.Assertions.assertThat(repository.findById(sli.getId())).isPresent();
        org.assertj.core.api.Assertions.assertThat(metricRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(repository.countSlosUsingSli(sli.getId())).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId())).isPresent();
    }

    @Test
    void deleteOfBusinessServiceWithSloIsConflict() throws Exception {
        OpenSlo bs = saveRecord("BusinessService", "bs");
        OpenSlo slo = saveRecord("SLO", "bs-slo");
        slo.setService(bs);
        repository.save(slo);

        mockMvc.perform(delete("/api/v1/openslo/" + bs.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(repository.findById(bs.getId())).isPresent();
    }

    @Test
    void deleteOfDataSourceWithSliIsConflict() throws Exception {
        OpenSlo ds = saveRecord("DataSource", "ds");
        OpenSlo sli = saveRecord("SLI", "ds-sli");
        sli.setDatasource(ds);
        repository.save(sli);

        mockMvc.perform(delete("/api/v1/openslo/" + ds.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(repository.findById(ds.getId())).isPresent();
    }

    @Test
    void deleteOfAlertingSourceDetachesItsSlos() throws Exception {
        OpenSlo source = saveRecord("AlertingSource", "alerts");
        OpenSlo slo = saveRecord("SLO", "alerted-slo");
        slo.setAlertingSource(source);
        repository.save(slo);

        mockMvc.perform(delete("/api/v1/openslo/" + source.getId())).andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId()).orElseThrow().getAlertingSource()).isNull();
    }

    @Test
    void deleteOfUnknownRecordIsNotFoundWithMessage() throws Exception {
        mockMvc.perform(delete("/api/v1/openslo/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateWithNameOfAnotherRecordIsConflictAndKeepsName() throws Exception {
        OpenSlo a = saveRecord("SLO", "name-a");
        saveRecord("SLO", "name-b");

        mockMvc.perform(put("/api/v1/openslo/" + a.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new OpenSlo("openslo/v1", "SLO", "name-b", "x", "{}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name-b")));

        org.assertj.core.api.Assertions.assertThat(repository.findById(a.getId()).orElseThrow().getName()).isEqualTo("name-a");
    }

    @Test
    void createWithExistingNameIsConflict() throws Exception {
        saveRecord("SLO", "taken");

        mockMvc.perform(post("/api/v1/openslo").contentType(MediaType.APPLICATION_JSON)
                .content(json(new OpenSlo("openslo/v1", "SLO", "taken", "x", "{}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateKeepingOwnNameIsOk() throws Exception {
        OpenSlo a = saveRecord("SLO", "own-name");

        mockMvc.perform(put("/api/v1/openslo/" + a.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new OpenSlo("openslo/v1", "SLO", "own-name", "changed", "{}"))))
                .andExpect(status().isOk());
    }

    @Test
    void updateOfUnknownRecordIsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/openslo/999999").contentType(MediaType.APPLICATION_JSON)
                .content(json(new OpenSlo("openslo/v1", "SLO", "ghost", "x", "{}"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateIgnoresLastRefreshTimeFromRequest() throws Exception {
        OpenSlo a = saveRecord("SLI", "refresh");

        mockMvc.perform(put("/api/v1/openslo/" + a.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiVersion\":\"openslo/v1\",\"kind\":\"SLI\",\"name\":\"refresh\",\"displayName\":\"r\","
                        + "\"spec\":\"{}\",\"lastRefreshTime\":\"2000-01-01T00:00:00\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(repository.findById(a.getId()).orElseThrow().getLastRefreshTime()).isNull();
    }

    @Test
    void reportsOnMissingOrWrongKindAreNotFound() throws Exception {
        OpenSlo sli = saveRecord("SLI", "r-sli");
        OpenSlo slo = saveRecord("SLO", "r-slo");

        mockMvc.perform(get("/api/v1/openslo/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(get("/api/v1/openslo/" + sli.getId() + "/report")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/openslo/" + slo.getId() + "/service-report")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/openslo/" + slo.getId() + "/sli-report")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/openslo/999999/report")).andExpect(status().isNotFound());
    }

    @Test
    void businessServiceSloLinksAreSavedAndCleared() throws Exception {
        OpenSlo bs = saveRecord("BusinessService", "link-bs");
        OpenSlo slo = saveRecord("SLO", "link-slo");

        mockMvc.perform(put("/api/v1/openslo/" + bs.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody("link-bs", "[{\"id\":" + slo.getId() + "}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slos[0].id").value(slo.getId()));
        org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId()).orElseThrow().getService().getId())
                .isEqualTo(bs.getId());

        mockMvc.perform(put("/api/v1/openslo/" + bs.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody("link-bs", "[]")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId()).orElseThrow().getService()).isNull();
    }

    @Test
    void businessServiceCanBeCreatedWithSloLinks() throws Exception {
        OpenSlo slo = saveRecord("SLO", "create-link-slo");

        mockMvc.perform(post("/api/v1/openslo").contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody("create-link-bs", "[{\"id\":" + slo.getId() + "}]")))
                .andExpect(status().isCreated());

        Long bsId = repository.findByName("create-link-bs").orElseThrow().getId();
        org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId()).orElseThrow().getService().getId())
                .isEqualTo(bsId);
    }

    @Test
    void invalidSloLinksAreBadRequestAndKeepExistingLinks() throws Exception {
        OpenSlo bs = saveRecord("BusinessService", "keep-bs");
        OpenSlo slo = saveRecord("SLO", "keep-slo");
        OpenSlo sli = saveRecord("SLI", "keep-sli");
        slo.setService(bs);
        repository.save(slo);

        String[] bad = {"[{\"id\":999999}]", "[{\"id\":" + sli.getId() + "}]", "[null]", "null"};
        for (String slos : bad) {
            mockMvc.perform(put("/api/v1/openslo/" + bs.getId()).contentType(MediaType.APPLICATION_JSON)
                    .content(serviceBody("keep-bs", slos)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
            org.assertj.core.api.Assertions.assertThat(repository.findById(slo.getId()).orElseThrow().getService().getId())
                    .isEqualTo(bs.getId());
        }
    }

    @Test
    void rejectedBusinessServiceCreateLeavesNoRecord() throws Exception {
        for (String slos : new String[]{"[{\"id\":999999}]", "null"}) {
            mockMvc.perform(post("/api/v1/openslo").contentType(MediaType.APPLICATION_JSON)
                    .content(serviceBody("never-created", slos)))
                    .andExpect(status().isBadRequest());
        }
        org.assertj.core.api.Assertions.assertThat(repository.findByName("never-created")).isEmpty();
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

    private String sourceBody(String name, String url) {
        return "{\"apiVersion\":\"openslo/v1\",\"kind\":\"AlertingSource\",\"name\":\"" + name
                + "\",\"displayName\":\"" + name + "\",\"spec\":\"{}\",\"alertUrl\":\"" + url + "\"}";
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "http://127.0.0.1/hook", "http://10.0.0.5/hook", "http://169.254.169.254/hook",
            "http://[::1]/hook", "http://192.0.2.1/hook", "ftp://example.invalid/hook"})
    void createAndUpdateRefuseUnsafeAlertUrls(String url) throws Exception {
        OpenSlo existing = saveRecord("AlertingSource", "safe-source");
        existing.setAlertUrl("https://93.184.216.34/safe");
        existing = repository.save(existing);
        long countBefore = repository.count();

        mockMvc.perform(post("/api/v1/openslo").contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody("new-source", url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(repository.count()).isEqualTo(countBefore);

        mockMvc.perform(put("/api/v1/openslo/" + existing.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody("safe-source", url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(repository.findById(existing.getId()).orElseThrow().getAlertUrl())
                .isEqualTo("https://93.184.216.34/safe");
    }

    @Test
    void maskedUrlOfAnotherHostIsRefusedOnUpdate() throws Exception {
        OpenSlo existing = saveRecord("AlertingSource", "masked-other");
        existing.setAlertUrl("https://93.184.216.34/safe");
        existing = repository.save(existing);

        mockMvc.perform(put("/api/v1/openslo/" + existing.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody("masked-other", "https://93.184.216.35/****")))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(repository.findById(existing.getId()).orElseThrow().getAlertUrl())
                .isEqualTo("https://93.184.216.34/safe");
    }

    @Test
    void alertUrlIsMaskedInResponsesAndMaskedPutKeepsTheStoredUrl() throws Exception {
        mockMvc.perform(post("/api/v1/openslo").contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody("public-source", "https://93.184.216.34/hook")))
                .andExpect(status().isCreated());
        OpenSlo stored = repository.findByName("public-source").orElseThrow();

        mockMvc.perform(get("/api/v1/openslo/" + stored.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertUrl").value("https://93.184.216.34/****"));

        mockMvc.perform(put("/api/v1/openslo/" + stored.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(sourceBody("public-source", "https://93.184.216.34/****")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(repository.findById(stored.getId()).orElseThrow().getAlertUrl())
                .isEqualTo("https://93.184.216.34/hook");
    }

    @Test
    void nestedAlertingSourceIsMaskedAndSloUpdateKeepsItsStoredUrl() throws Exception {
        OpenSlo source = saveRecord("AlertingSource", "nested-source");
        source.setAlertUrl("https://93.184.216.34/secret");
        source = repository.save(source);
        OpenSlo slo = saveRecord("SLO", "nested-slo");
        slo.setAlertingSource(source);
        slo = repository.save(slo);

        mockMvc.perform(get("/api/v1/openslo/" + slo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertingSource.alertUrl").value("https://93.184.216.34/****"));

        String body = "{\"apiVersion\":\"openslo/v1\",\"kind\":\"SLO\",\"name\":\"nested-slo\","
                + "\"displayName\":\"nested-slo display\",\"spec\":\"{}\",\"alertingSource\":{\"id\":"
                + source.getId() + ",\"alertUrl\":\"https://93.184.216.34/****\"}}";
        mockMvc.perform(put("/api/v1/openslo/" + slo.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(repository.findById(source.getId()).orElseThrow().getAlertUrl())
                .isEqualTo("https://93.184.216.34/secret");
    }
}
