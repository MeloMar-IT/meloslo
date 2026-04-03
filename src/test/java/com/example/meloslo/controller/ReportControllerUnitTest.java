package com.example.meloslo.controller;

import com.example.meloslo.service.ReportService;
import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
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
        when(reportService.generatePdfReport(anyList())).thenReturn(pdfContent);

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 3]"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(pdfContent));
    }

    @Test
    void shouldReturn3xxWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2, 3]"))
                .andExpect(status().is3xxRedirection());
    }
}
