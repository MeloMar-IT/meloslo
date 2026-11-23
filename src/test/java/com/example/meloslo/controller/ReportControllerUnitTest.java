package com.example.meloslo.controller;

import com.example.meloslo.dto.ReportOptionsDTO;
import com.example.meloslo.service.ReportService;
import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser
    void shouldDownloadPdfReport() throws Exception {
        byte[] pdfContent = "%PDF-1.4 test content".getBytes();
        when(reportService.generatePdfReport(any(ReportOptionsDTO.class))).thenReturn(pdfContent);

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [1, 2, 3], \"includeMetadata\": true, \"timeUnit\": \"YEARS\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(pdfContent));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"timeUnit\":\"DAYS\"}",
            "{\"ids\":[],\"timeUnit\":\"DAYS\"}",
            "{\"ids\":[null],\"timeUnit\":\"DAYS\"}",
            "{\"ids\":[1]}",
            "{\"ids\":[1],\"timeUnit\":null}",
            "{\"ids\":[1],\"timeUnit\":\"WEEKS\"}",
            "{\"ids\":[1],\"timeValue\":0,\"timeUnit\":\"DAYS\"}",
            "{\"ids\":[1],\"timeValue\":11,\"timeUnit\":\"YEARS\"}",
            "{\"ids\":[1],\"timeValue\":121,\"timeUnit\":\"MONTHS\"}",
            "{\"ids\":[1],\"timeValue\":3651,\"timeUnit\":\"DAYS\"}"
    })
    @WithMockUser
    void shouldRejectInvalidReportOptionsWithBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(reportService, never()).generatePdfReport(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ids\":[1],\"timeValue\":10,\"timeUnit\":\"years\"}",
            "{\"ids\":[1],\"timeValue\":3650,\"timeUnit\":\"DAYS\"}"
    })
    @WithMockUser
    void shouldAcceptPeriodsUpToTenYearsInAnyLetterCase(String body) throws Exception {
        when(reportService.generatePdfReport(any(ReportOptionsDTO.class))).thenReturn("%PDF".getBytes());

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnUnauthorizedWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [1, 2, 3]}"))
                .andExpect(status().isUnauthorized());
    }
}
