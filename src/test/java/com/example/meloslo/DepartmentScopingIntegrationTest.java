package com.example.meloslo;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Department scoping over the real security filter chain and database. The class owns its data:
 * it empties the record tables before each test and again afterwards, so SecurityIntegrationTest
 * (which seeds only when fewer than four records exist) starts clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DepartmentScopingIntegrationTest {

    private static final String BASE = "/api/v1/openslo";

    @Autowired private MockMvc mockMvc;
    @Autowired private OpenSloRepository repository;
    @Autowired private MetricRepository metricRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static boolean createdTestUser;

    private OpenSlo finSvc;
    private OpenSlo finSli;
    private OpenSlo finSlo;
    private OpenSlo infraSvc;
    private OpenSlo infraSli;
    private OpenSlo infraSlo;
    private OpenSlo noDeptSlo;
    private OpenSlo crossSlo;
    private OpenSlo bizSvc;

    private final RequestPostProcessor testuser = user("testuser").roles("USER");
    private final RequestPostProcessor nodept = user("nodept").roles("USER");
    private final RequestPostProcessor admin = user("admin").roles("ADMIN");

    private void wipe() {
        jdbc.update("DELETE FROM slo_slis");
        jdbc.update("UPDATE openslo_records SET service_id = NULL, datasource_id = NULL, alerting_source_id = NULL");
        metricRepository.deleteAll();
        repository.deleteAll();
    }

    private OpenSlo save(String kind, String name, String department) {
        OpenSlo r = new OpenSlo("openslo/v1", kind, name, name + " display", "{}");
        r.setDepartment(department);
        return repository.save(r);
    }

    @BeforeEach
    void seed() {
        wipe();
        if (userRepository.findByUsername("testuser").isEmpty()) {
            userRepository.save(new User("testuser", "x", "testuser@finance.com", "Finance,Engineering"));
            createdTestUser = true;
        }
        userRepository.findByUsername("nodept").ifPresent(userRepository::delete);
        userRepository.save(new User("nodept", "x", "nodept@test.com", null));

        finSvc = save("BusinessService", "it-fin-svc", "Finance");
        finSli = save("SLI", "it-fin-sli", "Finance");
        finSlo = linkedSlo("it-fin-slo", "Finance", finSvc, finSli);
        infraSvc = save("BusinessService", "it-infra-svc", "Infrastructure");
        infraSli = save("SLI", "it-infra-sli", "Infrastructure");
        infraSlo = linkedSlo("it-infra-slo", "Infrastructure", infraSvc, infraSli);
        noDeptSlo = save("SLO", "it-nodept-slo", null);

        OpenSlo infraAlert = save("AlertingSource", "it-infra-alert", "Infrastructure");
        OpenSlo cross = new OpenSlo("openslo/v1", "SLO", "it-cross-slo", "it-cross-slo display", "{}");
        cross.setDepartment("Finance");
        cross.setSlis(new ArrayList<>(List.of(infraSli)));
        cross.setAlertingSource(infraAlert);
        crossSlo = repository.save(cross);

        bizSvc = save("BusinessService", "it-biz-svc", "Finance");
        linkedSlo("it-biz-fin-child", "Finance", bizSvc, null);
        linkedSlo("it-biz-infra-child", "Infrastructure", bizSvc, null);

        metricRepository.save(new SliMetric(LocalDateTime.now().minusHours(1), 0.5, finSli, null));
        metricRepository.save(new SliMetric(LocalDateTime.now().minusHours(1), 0.4, infraSli, null));
    }

    private OpenSlo linkedSlo(String name, String department, OpenSlo service, OpenSlo sli) {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", name, name + " display", "{}");
        slo.setDepartment(department);
        slo.setService(service);
        if (sli != null) {
            slo.setSlis(new ArrayList<>(List.of(sli)));
        }
        return repository.save(slo);
    }

    @AfterAll
    static void cleanUp(@Autowired JdbcTemplate jdbc, @Autowired MetricRepository metrics,
                        @Autowired OpenSloRepository records, @Autowired UserRepository users) {
        jdbc.update("DELETE FROM slo_slis");
        jdbc.update("UPDATE openslo_records SET service_id = NULL, datasource_id = NULL, alerting_source_id = NULL");
        metrics.deleteAll();
        records.deleteAll();
        users.findByUsername("nodept").ifPresent(users::delete);
        if (createdTestUser) {
            users.findByUsername("testuser").ifPresent(users::delete);
        }
    }

    private String body(String kind, String name, String department) throws Exception {
        OpenSlo r = new OpenSlo("openslo/v1", kind, name, name + " display", "{}");
        r.setDepartment(department);
        return objectMapper.writeValueAsString(r);
    }

    private String bodyWithId(Long id, String name, String department) throws Exception {
        OpenSlo r = new OpenSlo("openslo/v1", "SLO", name, "Hijacked", "{}");
        r.setId(id);
        r.setDepartment(department);
        return objectMapper.writeValueAsString(r);
    }

    // ---- other department's ids: 404 on every route ----

    @Test
    void userGetsNotFoundForOtherDepartmentOnEveryIdRoute() throws Exception {
        mockMvc.perform(get(BASE + "/" + infraSlo.getId()).with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + infraSlo.getId() + "/report").with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + infraSvc.getId() + "/service-report").with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + infraSli.getId() + "/sli-report").with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/" + infraSlo.getId()).with(testuser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-infra-slo", "Finance"))).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + infraSli.getId()).with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + infraSlo.getId()).with(testuser)).andExpect(status().isNotFound());

        assertThat(repository.findById(infraSli.getId())).isPresent();
        OpenSlo unchanged = repository.findById(infraSlo.getId()).orElseThrow();
        assertThat(unchanged.getDisplayName()).isEqualTo("it-infra-slo display");
        assertThat(unchanged.getDepartment()).isEqualTo("Infrastructure");
    }

    @Test
    void userGetsOkForOwnDepartmentRoutes() throws Exception {
        mockMvc.perform(get(BASE + "/" + finSlo.getId()).with(testuser)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + finSlo.getId() + "/report").with(testuser)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + finSvc.getId() + "/service-report").with(testuser)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + finSli.getId() + "/sli-report").with(testuser)).andExpect(status().isOk());
    }

    @Test
    void financeSloLinkedToInfrastructureShowsNoInfrastructureData() throws Exception {
        mockMvc.perform(get(BASE + "/" + crossSlo.getId()).with(testuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slis", hasSize(0)))
                .andExpect(jsonPath("$.alertingSource").doesNotExist());
        mockMvc.perform(get(BASE + "/" + crossSlo.getId() + "/report").with(testuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentMetrics", hasSize(0)));
        mockMvc.perform(get(BASE).with(testuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='it-cross-slo')].slis[0].name").isEmpty())
                .andExpect(jsonPath("$[?(@.name=='it-cross-slo')].alertingSource.name").isEmpty());

        // The links themselves are untouched, and admins still see them.
        mockMvc.perform(get(BASE + "/" + crossSlo.getId()).with(admin))
                .andExpect(jsonPath("$.slis", hasSize(1)))
                .andExpect(jsonPath("$.alertingSource.name").value("it-infra-alert"));
        mockMvc.perform(get(BASE + "/" + crossSlo.getId() + "/report").with(admin))
                .andExpect(jsonPath("$.recentMetrics", hasSize(1)));
    }

    @Test
    void serviceReportAggregatesOnlyVisibleChildren() throws Exception {
        mockMvc.perform(get(BASE + "/" + bizSvc.getId() + "/service-report").with(testuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sloReports", hasSize(1)))
                .andExpect(jsonPath("$.sloReports[0].name").value("it-biz-fin-child"));
        mockMvc.perform(get(BASE + "/" + bizSvc.getId() + "/service-report").with(admin))
                .andExpect(jsonPath("$.sloReports", hasSize(2)));
    }

    // ---- create and update ----

    @Test
    void movingRecordToForeignDepartmentIsForbiddenAndNotStored() throws Exception {
        mockMvc.perform(put(BASE + "/" + finSlo.getId()).with(testuser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-fin-slo", "Infrastructure")))
                .andExpect(status().isForbidden());

        assertThat(repository.findById(finSlo.getId()).orElseThrow().getDepartment()).isEqualTo("Finance");
    }

    @Test
    void userCanUpdateWithinOwnDepartments() throws Exception {
        mockMvc.perform(put(BASE + "/" + finSlo.getId()).with(testuser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-fin-slo", "Engineering")))
                .andExpect(status().isOk());

        assertThat(repository.findById(finSlo.getId()).orElseThrow().getDepartment()).isEqualTo("Engineering");
    }

    @Test
    void createIsCheckedAgainstCallerDepartments() throws Exception {
        mockMvc.perform(post(BASE).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-new-hr", "HR"))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-new-none", null))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-new-fin", "finance"))).andExpect(status().isCreated());

        assertThat(repository.findByName("it-new-hr")).isEmpty();
        assertThat(repository.findByName("it-new-none")).isEmpty();
        assertThat(repository.findByName("it-new-fin")).isPresent();
    }

    @Test
    void servicePutNeverUnlinksOrLinksSlosOfOtherDepartments() throws Exception {
        OpenSlo finChild = repository.findByName("it-biz-fin-child").orElseThrow();
        OpenSlo infraChild = repository.findByName("it-biz-infra-child").orElseThrow();
        String prefix = "{\"apiVersion\":\"openslo/v1\",\"kind\":\"BusinessService\",\"name\":\"it-biz-svc\","
                + "\"displayName\":\"x\",\"spec\":\"{}\",\"department\":\"Finance\",\"slos\":";

        mockMvc.perform(put(BASE + "/" + bizSvc.getId()).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(prefix + "[{\"id\":" + finChild.getId() + "}]}"))
                .andExpect(status().isOk());
        assertThat(repository.findById(infraChild.getId()).orElseThrow().getService().getId()).isEqualTo(bizSvc.getId());

        mockMvc.perform(put(BASE + "/" + bizSvc.getId()).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(prefix + "[{\"id\":" + infraChild.getId() + "}]}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.findById(finChild.getId()).orElseThrow().getService().getId()).isEqualTo(bizSvc.getId());
        assertThat(repository.findById(infraChild.getId()).orElseThrow().getService().getId()).isEqualTo(bizSvc.getId());
    }

    @Test
    void createWithExistingIdIsRejectedAndDoesNotOverwrite() throws Exception {
        mockMvc.perform(post(BASE).with(testuser).contentType(MediaType.APPLICATION_JSON)
                .content(bodyWithId(infraSlo.getId(), "it-infra-slo", "Finance")))
                .andExpect(status().isBadRequest());

        OpenSlo unchanged = repository.findById(infraSlo.getId()).orElseThrow();
        assertThat(unchanged.getDepartment()).isEqualTo("Infrastructure");
        assertThat(unchanged.getDisplayName()).isEqualTo("it-infra-slo display");
    }

    // ---- list and by-id agree ----

    @Test
    void listAndByIdAgreeForUser() throws Exception {
        mockMvc.perform(get(BASE).with(testuser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("it-fin-slo")))
                .andExpect(jsonPath("$[*].name", not(hasItem("it-infra-slo"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("it-nodept-slo"))));

        mockMvc.perform(get(BASE + "/" + finSlo.getId()).with(testuser)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + infraSlo.getId()).with(testuser)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + noDeptSlo.getId()).with(testuser)).andExpect(status().isNotFound());
    }

    @Test
    void userWithoutDepartmentsSeesNothing() throws Exception {
        mockMvc.perform(get(BASE).with(nodept))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get(BASE + "/" + finSlo.getId()).with(nodept)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/dashboard-stats").with(nodept))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBusinessServices").value(0))
                .andExpect(jsonPath("$.totalSlos").value(0))
                .andExpect(jsonPath("$.healthySlos").value(0))
                .andExpect(jsonPath("$.warningSlos").value(0))
                .andExpect(jsonPath("$.breachingSlos").value(0));
        mockMvc.perform(post(BASE).with(nodept).contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-new-nodept", "Finance"))).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAndEditsEverything() throws Exception {
        mockMvc.perform(get(BASE).with(admin))
                .andExpect(jsonPath("$[*].name", hasItem("it-infra-slo")))
                .andExpect(jsonPath("$[*].name", hasItem("it-nodept-slo")))
                .andExpect(jsonPath("$[*].name", hasItem("it-fin-slo")));
        mockMvc.perform(get(BASE + "/" + noDeptSlo.getId()).with(admin)).andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/" + infraSlo.getId()).with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SLO", "it-infra-slo", "Infrastructure")))
                .andExpect(status().isOk());
    }

    @Test
    void metricsDoNotExposeForeignDatasource() throws Exception {
        OpenSlo infraDs = save("DataSource", "it-infra-ds", "Infrastructure");
        metricRepository.save(new SliMetric(LocalDateTime.now().minusHours(2), 0.7, finSli, infraDs));

        mockMvc.perform(get(BASE + "/" + finSli.getId() + "/sli-report").with(testuser))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("it-infra-ds"))));
        mockMvc.perform(get(BASE + "/" + finSli.getId() + "/sli-report").with(admin))
                .andExpect(content().string(containsString("it-infra-ds")));
    }

    @Test
    void userCannotDeleteAlertingSourceUsedByForeignSlo() throws Exception {
        OpenSlo finAlert = save("AlertingSource", "it-fin-alert", "Finance");
        OpenSlo infraSloAlerted = new OpenSlo("openslo/v1", "SLO", "it-infra-alerted", "it-infra-alerted display", "{}");
        infraSloAlerted.setDepartment("Infrastructure");
        infraSloAlerted.setAlertingSource(finAlert);
        infraSloAlerted = repository.save(infraSloAlerted);

        mockMvc.perform(delete(BASE + "/" + finAlert.getId()).with(testuser))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/" + finAlert.getId()).with(admin)).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + infraSloAlerted.getId()).with(admin))
                .andExpect(jsonPath("$.alertingSource.name").value("it-fin-alert"));
    }

    // ---- PDF report ----

    private String pdfText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                sb.append(extractor.getTextFromPage(p)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private byte[] pdfFor(RequestPostProcessor who, Long... ids) throws Exception {
        String json = "{\"ids\":" + java.util.Arrays.toString(ids) + ",\"timeUnit\":\"YEARS\"}";
        return mockMvc.perform(post("/api/v1/reports/pdf").with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    @Test
    void pdfOmitsRecordsTheCallerCannotSee() throws Exception {
        assertThat(pdfText(pdfFor(testuser, infraSlo.getId()))).doesNotContain("it-infra-slo display");
        assertThat(pdfText(pdfFor(testuser, finSlo.getId()))).contains("it-fin-slo display");
    }

    @Test
    void pdfListsOnlyAllowedChildrenOfBusinessService() throws Exception {
        String text = pdfText(pdfFor(testuser, bizSvc.getId()));

        assertThat(text).contains("it-biz-fin-child display");
        assertThat(text).doesNotContain("it-biz-infra-child display");
    }
}
