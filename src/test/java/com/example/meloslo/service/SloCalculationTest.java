package com.example.meloslo.service;

import com.example.meloslo.dto.SloReport;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class SloCalculationTest {

    private OpenSloService service;

    @Mock
    private OpenSloRepository repository;

    @Mock
    private MetricRepository metricRepository;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new OpenSloService(repository, metricRepository, userRepository);
    }

    @Test
    void testGetSloReport() {
        // Given
        OpenSlo sli = new OpenSlo();
        sli.setId(1L);
        sli.setKind("SLI");
        sli.setName("test-sli");

        OpenSlo slo = new OpenSlo();
        slo.setId(2L);
        slo.setKind("SLO");
        slo.setName("test-slo");
        slo.setSpec("spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d");
        slo.setSlis(List.of(sli));

        when(repository.findById(2L)).thenReturn(Optional.of(slo));

        List<SliMetric> metrics = new ArrayList<>();
        metrics.add(new SliMetric(LocalDateTime.now().minusDays(1), 0.90, sli, null));
        metrics.add(new SliMetric(LocalDateTime.now().minusDays(2), 1.00, sli, null));

        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(eq(sli), any(LocalDateTime.class)))
                .thenReturn(metrics);

        // When
        SloReport report = service.getSloReport(2L);

        // Then
        assertNotNull(report);
        assertEquals("test-slo", report.getName());
        assertEquals(0.95, report.getTarget());
        assertEquals(0.95, report.getCurrentValue()); // (0.9 + 1.0) / 2 = 0.95
        assertEquals(0.0, report.getErrorBudget(), 0.001); // (0.95 - 0.95) / (1 - 0.95) = 0
    }

    @Test
    void testGetSloReportDefaultTarget() {
        // Given
        OpenSlo slo = new OpenSlo();
        slo.setId(3L);
        slo.setKind("SLO");
        slo.setName("default-slo");
        slo.setSpec("spec: {}"); // No objectives

        when(repository.findById(3L)).thenReturn(Optional.of(slo));

        // When
        SloReport report = service.getSloReport(3L);

        // Then
        assertEquals(0.99, report.getTarget()); // Default target
    }
}
