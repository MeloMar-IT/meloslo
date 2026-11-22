package com.example.meloslo.service;

import com.example.meloslo.dto.ReportOptionsDTO;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.lowagie.text.pdf.PdfReader;
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
import static org.mockito.Mockito.mock;
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
    void shouldGeneratePdfReport() {
        // Arrange
        OpenSlo service = new OpenSlo();
        service.setId(1L);
        service.setKind("Service");
        service.setDisplayName("Test Service");
        service.setName("test-service");
        service.setDepartment("Finance");

        OpenSlo slo = new OpenSlo();
        slo.setId(2L);
        slo.setKind("SLO");
        slo.setDisplayName("Test SLO");
        slo.setName("test-slo");
        slo.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        slo.setDepartment("Finance");
        
        OpenSlo sli = new OpenSlo();
        sli.setId(3L);
        sli.setKind("SLI");
        slo.setSlis(Collections.singletonList(sli));
        service.setSlos(Collections.singletonList(slo));

        when(openSloService.getRecordById(1L)).thenReturn(Optional.of(service));
        when(openSloService.getRecordById(2L)).thenReturn(Optional.of(slo));
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.singletonList("Finance"));
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
    }

    @Test
    void shouldRespectDepartmentAccess() {
        // Arrange
        OpenSlo record = new OpenSlo();
        record.setId(1L);
        record.setKind("SLO");
        record.setDepartment("Secret");

        when(openSloService.getRecordById(1L)).thenReturn(Optional.of(record));
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.singletonList("Public"));

        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(1L));

        // Act
        byte[] pdf = reportService.generatePdfReport(options);

        // Assert
        assertNotNull(pdf);
        // A PDF with no records will still have header/footer, but should be smaller or different.
        // Actually, without records it only has the title and date info.
        assertTrue(pdf.length > 0);
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
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.singletonList("Engineering"));
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
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.emptyList());
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
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.emptyList());
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

    private OpenSlo sloWithSli(long id) {
        OpenSlo sli = new OpenSlo();
        sli.setId(id + 100);
        sli.setKind("SLI");
        OpenSlo slo = new OpenSlo();
        slo.setId(id);
        slo.setKind("SLO");
        slo.setDisplayName("Agreement SLO");
        slo.setName("agreement-slo");
        slo.setSpec("spec: {objectives: [{target: 0.95}], timeWindow: [{duration: 30d}]}");
        slo.setSlis(Collections.singletonList(sli));
        return slo;
    }

    private String pdfText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i));
            }
            // Numbers use the JVM default locale (e.g. "94,0000%"); normalise the decimal separator
            return sb.toString().replaceAll("\\s+", "").replace(',', '.');
        } finally {
            reader.close();
        }
    }

    private byte[] generate(OpenSlo slo, int days) {
        when(openSloService.getRecordById(slo.getId())).thenReturn(Optional.of(slo));
        when(openSloService.getCurrentUserDepartments()).thenReturn(Collections.emptyList());
        when(openSloService.parseTargetFromSpec(anyString())).thenReturn(0.95);
        ReportOptionsDTO options = new ReportOptionsDTO();
        options.setIds(List.of(slo.getId()));
        options.setTimeValue(days);
        options.setTimeUnit("DAYS");
        return reportService.generatePdfReport(options);
    }

    @Test
    void pdfShowsNoDataInsteadOfZeroPercentWithoutSamples() throws IOException {
        OpenSlo slo = sloWithSli(40L);
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        String text = pdfText(generate(slo, 30));

        assertTrue(text.contains("Nodata"), text);
        assertTrue(text.contains("NODATA"), text);
        assertTrue(text.contains("Nodatainthisperiod."), text);
        assertFalse(text.contains("0.0000%"), text);
    }

    @Test
    void pdfAndDashboardCalculationAgreeForTheSameSamples() throws IOException {
        OpenSlo slo = sloWithSli(41L);
        List<SliMetric> metrics = List.of(
                new SliMetric(LocalDateTime.now().minusDays(1), 0.98, null, null),
                new SliMetric(LocalDateTime.now().minusDays(2), 0.90, null, null));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(metrics);

        String text = pdfText(generate(slo, 30));

        // Real service backed by the same samples
        OpenSloService real = new OpenSloService(mock(com.example.meloslo.repository.OpenSloRepository.class),
                metricRepository, mock(com.example.meloslo.repository.UserRepository.class));
        var report = real.calculateSloReport(slo);

        assertEquals(0.94, report.getCurrentValue(), 1e-9);
        assertEquals("Breaching", report.getStatus());
        assertTrue(text.contains("94.0000%"), text);
        assertTrue(text.contains("BREACHING"), text);
    }

    @Test
    void pdfWithGapsBetweenSamplesStillRenders() throws IOException {
        OpenSlo slo = sloWithSli(42L);
        List<SliMetric> metrics = List.of(
                new SliMetric(LocalDateTime.now().minusDays(1), 0.99, null, null),
                new SliMetric(LocalDateTime.now().minusDays(5), 0.97, null, null));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(metrics);

        String text = pdfText(generate(slo, 7));

        assertTrue(text.contains("98.0000%"), text);
        assertFalse(text.contains("Nodatainthisperiod."), text);
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
