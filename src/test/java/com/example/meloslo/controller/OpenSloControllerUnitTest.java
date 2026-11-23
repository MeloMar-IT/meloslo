package com.example.meloslo.controller;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.service.BadRequestException;
import com.example.meloslo.service.ConflictException;
import com.example.meloslo.service.InvalidSliValueException;
import com.example.meloslo.service.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpenSloController.class)
@Import({SecurityConfig.class, com.example.meloslo.CsrfTestConfig.class})
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
                .andExpect(jsonPath("$.totalBusinessServices").value(5))
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
        when(service.updateRecord(eq(99L), any(OpenSlo.class))).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(put("/api/v1/openslo/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void shouldReturnConflictWhenUpdateHitsDuplicateName() throws Exception {
        when(service.updateRecord(eq(1L), any(OpenSlo.class))).thenThrow(new ConflictException("dup"));

        mockMvc.perform(put("/api/v1/openslo/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("dup"));
    }

    @Test
    void shouldReturnConflictWhenDeletingRecordInUse() throws Exception {
        doThrow(new ConflictException("in use")).when(service).deleteRecord(5L);

        mockMvc.perform(delete("/api/v1/openslo/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("in use"));
    }

    @Test
    void shouldReturnNotFoundForReportsOnMissingOrWrongKind() throws Exception {
        when(service.getSloReport(7L)).thenThrow(new ResourceNotFoundException("no slo"));
        when(service.getServiceReport(7L)).thenThrow(new ResourceNotFoundException("no svc"));
        when(service.getSliReport(7L)).thenThrow(new ResourceNotFoundException("no sli"));

        mockMvc.perform(get("/api/v1/openslo/7/report")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("no slo"));
        mockMvc.perform(get("/api/v1/openslo/7/service-report")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/openslo/7/sli-report")).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWithMessageForUnknownRecord() throws Exception {
        when(service.getRecordById(404L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/openslo/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldHideSqlWhenCreateViolatesConstraint() throws Exception {
        when(service.createRecord(any(OpenSlo.class)))
                .thenThrow(new DataIntegrityViolationException("SQL constraint UK_NAME violated"));

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message", not(containsString("SQL"))));
    }

    @Test
    void shouldReturnBadRequestWithMessageForInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"SLO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldNotMapUnexpectedRuntimeExceptionTo404() {
        RuntimeException boom = new RuntimeException("boom");
        when(service.getSliReport(8L)).thenThrow(boom);

        jakarta.servlet.ServletException ex = org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> mockMvc.perform(get("/api/v1/openslo/8/sli-report")));
        org.junit.jupiter.api.Assertions.assertSame(boom, ex.getCause());
    }

    @Test
    void shouldReturnBadRequestWhenCreatingRecordWithInvalidTarget() throws Exception {
        when(service.createRecord(any(OpenSlo.class))).thenThrow(new InvalidSliValueException("bad target"));

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad target"));
    }

    @Test
    void shouldReturnBadRequestNotNotFoundWhenUpdatingRecordWithInvalidTarget() throws Exception {
        when(service.updateRecord(eq(1L), any(OpenSlo.class))).thenThrow(new InvalidSliValueException("bad target"));

        mockMvc.perform(put("/api/v1/openslo/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad target"));
    }

    @Test
    void shouldDeleteRecord() throws Exception {
        mockMvc.perform(delete("/api/v1/openslo/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnForbiddenWhenCreatingInForeignDepartment() throws Exception {
        when(service.createRecord(any(OpenSlo.class))).thenThrow(new AccessDeniedException("no"));

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnBadRequestWhenCreatingWithId() throws Exception {
        when(service.createRecord(any(OpenSlo.class))).thenThrow(new BadRequestException("id"));

        mockMvc.perform(post("/api/v1/openslo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("id"));
    }

    @Test
    void shouldReturnForbiddenWhenMovingRecordToForeignDepartment() throws Exception {
        when(service.updateRecord(eq(1L), any(OpenSlo.class))).thenThrow(new AccessDeniedException("no"));

        mockMvc.perform(put("/api/v1/openslo/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(slo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownOrForeignRecord() throws Exception {
        doThrow(new ResourceNotFoundException("not found")).when(service).deleteRecord(99L);

        mockMvc.perform(delete("/api/v1/openslo/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnForbiddenWhenDeleteIsDenied() throws Exception {
        doThrow(new AccessDeniedException("no")).when(service).deleteRecord(98L);

        mockMvc.perform(delete("/api/v1/openslo/98"))
                .andExpect(status().isForbidden());
    }
}
