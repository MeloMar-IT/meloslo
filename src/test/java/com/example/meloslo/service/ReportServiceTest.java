package com.example.meloslo.service;

import com.example.meloslo.dto.ReportOptionsDTO;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private ReportService reportService;

    @Mock
    private OpenSloService openSloService;

    @Mock
    private MetricRepository metricRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reportService = new ReportService(openSloService, metricRepository);
    }

    @Test
    void shouldGeneratePdfReport() throws IOException {
        // Arrange
        OpenSlo service = new OpenSlo();
        service.setId(1L);
        service.setKind("BusinessService");
        service.setDisplayName("Test Service");
        service.setName("test-service");
        service.setDepartment("Finance");

        OpenSlo slo = new OpenSlo();
        slo.setId(2L);
        slo.setKind("SLO");
        slo.setDisplayName("Allowed SLO");
        slo.setName("test-slo");
        slo.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        slo.setDepartment("Finance");

        OpenSlo forbidden = new OpenSlo();
        forbidden.setId(4L);
        forbidden.setKind("SLO");
        forbidden.setDisplayName("Forbidden SLO");
        forbidden.setName("forbidden-slo");
        forbidden.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        forbidden.setDepartment("Infrastructure");

        OpenSlo sli = new OpenSlo();
        sli.setId(3L);
        sli.setKind("SLI");
        slo.setSlis(Collections.singletonList(sli));
        service.setSlos(List.of(slo, forbidden));

        when(openSloService.getRecordById(1L)).thenReturn(Optional.of(service));
        when(openSloService.getRecordById(2L)).thenReturn(Optional.of(slo));
        when(openSloService.getAccessibleSlos(service)).thenReturn(Collections.singletonList(slo));
        when(openSloService.parseTargetFromSpec(anyString())).thenReturn(0.95);
        
        List<SliMetric> metrics = new ArrayList<>();
        metrics.add(new SliMetric(LocalDateTime.now(), 0.98, sli, null));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(metrics);

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(1L, 2L));

        // Act
        byte[] pdf = reportService.generatePdfReport(options);

        // Assert
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        // Basic PDF header check
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);

        String text = pdfText(pdf);
        assertTrue(text.contains("Allowed SLO"));
        assertFalse(text.contains("Forbidden SLO"));
    }

    private String pdfText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(extractor.getTextFromPage(page)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    void shouldOmitRecordsTheCallerCannotSee() throws IOException {
        // getRecordById answers empty for records outside the caller's departments
        when(openSloService.getRecordById(1L)).thenReturn(Optional.empty());

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(1L));

        byte[] pdf = reportService.generatePdfReport(options);

        assertNotNull(pdf);
        assertFalse(pdfText(pdf).contains("Secret SLO"));
        verifyNoInteractions(metricRepository);
    }

    @Test
    void shouldIncludeRecordReturnedByServiceRegardlessOfDepartmentCase() throws IOException {
        OpenSlo record = new OpenSlo();
        record.setId(1L);
        record.setKind("SLO");
        record.setDisplayName("Lowercase Dept SLO");
        record.setName("lc");
        record.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        record.setDepartment("finance");

        when(openSloService.getRecordById(1L)).thenReturn(Optional.of(record));
        when(openSloService.parseTargetFromSpec(anyString())).thenReturn(0.95);

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(1L));

        assertTrue(pdfText(reportService.generatePdfReport(options)).contains("Lowercase Dept SLO"));
    }

    @Test
    void shouldGeneratePdfEvenWithNoMetrics() {
        // Arrange
        OpenSlo slo = new OpenSlo();
        slo.setId(10L);
        slo.setKind("SLO");
        slo.setDisplayName("No Data SLO");
        slo.setName("no-data-slo");
        slo.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        slo.setDepartment("Engineering");

        when(openSloService.getRecordById(10L)).thenReturn(Optional.of(slo));
        when(openSloService.parseTargetFromSpec(anyString())).thenReturn(0.95);
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(10L));

        // Act
        byte[] pdf = reportService.generatePdfReport(options);

        // Assert
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    void shouldHandleVariousTimePeriods() {
        // Arrange
        OpenSlo slo = new OpenSlo();
        slo.setId(20L);
        slo.setKind("SLO");
        slo.setDisplayName("Time Period SLO");
        slo.setDepartment("Engineering");
        slo.setSpec("target: 0.9");

        when(openSloService.getRecordById(20L)).thenReturn(Optional.of(slo));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(20L));
        
        // Test DAYS
        options.setTimeValue(7);
        options.setTimeUnit("DAYS");
        byte[] pdfDays = reportService.generatePdfReport(options);
        assertNotNull(pdfDays);

        // Test MONTHS
        options.setTimeValue(3);
        options.setTimeUnit("MONTHS");
        byte[] pdfMonths = reportService.generatePdfReport(options);
        assertNotNull(pdfMonths);
        
        // Test YEARS
        options.setTimeValue(2);
        options.setTimeUnit("YEARS");
        byte[] pdfYears = reportService.generatePdfReport(options);
        assertNotNull(pdfYears);
    }

    @Test
    void shouldHandleOptionalCharts() {
        // Arrange
        OpenSlo slo = new OpenSlo();
        slo.setId(30L);
        slo.setKind("SLO");
        slo.setDisplayName("Optional Chart SLO");
        slo.setDepartment("Engineering");
        slo.setSpec("target: 0.99");

        when(openSloService.getRecordById(30L)).thenReturn(Optional.of(slo));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(30L));
        
        // Disable charts
        options.setIncludeSloCharts(false);
        byte[] pdfNoCharts = reportService.generatePdfReport(options);
        assertNotNull(pdfNoCharts);

        // Enable only performance chart
        options.setIncludeSloCharts(true);
        options.setIncludeMetricsChart(true);
        options.setIncludeErrorBudgetChart(false);
        byte[] pdfPerfChartOnly = reportService.generatePdfReport(options);
        assertNotNull(pdfPerfChartOnly);

        // Enable only EB chart
        options.setIncludeMetricsChart(false);
        options.setIncludeErrorBudgetChart(true);
        byte[] pdfEbChartOnly = reportService.generatePdfReport(options);
        assertNotNull(pdfEbChartOnly);
        
        // Enable both
        options.setIncludeMetricsChart(true);
        options.setIncludeErrorBudgetChart(true);
        byte[] pdfBothCharts = reportService.generatePdfReport(options);
        assertNotNull(pdfBothCharts);
    }

    @Test
    void shouldGenerateA4SizedPdf() throws IOException {
        // Arrange
        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(Collections.emptyList());

        // Act
        byte[] pdf = reportService.generatePdfReport(options);

        // Assert
        assertNotNull(pdf);
        PdfReader reader = new PdfReader(pdf);
        assertEquals(595.0f, reader.getPageSize(1).getWidth(), 1.0f);
        assertEquals(842.0f, reader.getPageSize(1).getHeight(), 1.0f);
        reader.close();
    }
}
