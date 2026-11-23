package com.example.meloslo;

import com.example.meloslo.dto.ReportOptionsDTO;
import com.example.meloslo.dto.ReportTimeUnit;
import com.example.meloslo.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ReportFunctionalTest extends BaseFunctionalTest {

    @MockBean
    private ReportService reportService;

    @Test
    void shouldDownloadPdfReport() {
        byte[] pdfContent = "%PDF-1.4 test content".getBytes();
        when(reportService.generatePdfReport(any(ReportOptionsDTO.class))).thenReturn(pdfContent);

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(Collections.singletonList(1L));
        options.setIncludeMetadata(true);
        options.setTimeUnit(ReportTimeUnit.YEARS);

        ResponseEntity<byte[]> response = userAuth().postForEntity("/api/v1/reports/pdf", options, byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().get("Content-Disposition").get(0)).contains("attachment");
        assertThat(response.getBody()).isEqualTo(pdfContent);
    }
}
