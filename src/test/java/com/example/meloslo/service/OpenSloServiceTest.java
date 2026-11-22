package com.example.meloslo.service;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.BusinessServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OpenSloServiceTest {

    @Mock
    private OpenSloRepository repository;

    @Mock
    private MetricRepository metricRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OpenSloService service;

    private OpenSlo slo;

    @BeforeEach
    void setUp() {
        slo = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "{}");
        slo.setId(1L);
    }

    @Test
    void shouldGetAllRecords() {
        // Clear security context to ensure no department filtering
        SecurityContextHolder.clearContext();

        when(repository.findAll()).thenReturn(Arrays.asList(slo));

        List<OpenSlo> results = service.getAllRecords();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("TestSLO");
        verify(repository, times(1)).findAll();
    }

    @Test
    void shouldFilterRecordsByMultipleDepartments() {
        // Given
        User user = new User("testuser", "pass", "test@test.com", "Finance,Engineering");
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("testuser");
        when(auth.getName()).thenReturn("testuser");
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        OpenSlo financeSlo = new OpenSlo("openslo/v1", "SLO", "FinanceSLO", "Finance SLO", "{}");
        financeSlo.setDepartment("Finance");
        OpenSlo engSlo = new OpenSlo("openslo/v1", "SLO", "EngSLO", "Engineering SLO", "{}");
        engSlo.setDepartment("Engineering");
        OpenSlo hrSlo = new OpenSlo("openslo/v1", "SLO", "HRSLO", "HR SLO", "{}");
        hrSlo.setDepartment("HR");

        when(repository.findAll()).thenReturn(Arrays.asList(financeSlo, engSlo, hrSlo));

        // When
        List<OpenSlo> results = service.getAllRecords();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.stream().anyMatch(r -> r.getName().equals("FinanceSLO"))).isTrue();
        assertThat(results.stream().anyMatch(r -> r.getName().equals("EngSLO"))).isTrue();
        assertThat(results.stream().noneMatch(r -> r.getName().equals("HRSLO"))).isTrue();
        
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetRecordById() {
        when(repository.findById(1L)).thenReturn(Optional.of(slo));

        Optional<OpenSlo> result = service.getRecordById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("TestSLO");
    }

    @Test
    void shouldGetRecordByName() {
        when(repository.findByName("TestSLO")).thenReturn(Optional.of(slo));

        Optional<OpenSlo> result = service.getRecordByName("TestSLO");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void shouldCreateRecord() {
        when(repository.save(any(OpenSlo.class))).thenReturn(slo);

        OpenSlo created = service.createRecord(new OpenSlo());

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(1L);
        verify(repository, times(1)).save(any(OpenSlo.class));
    }

    @Test
    void shouldUpdateRecord() {
        OpenSlo updatedSlo = new OpenSlo("openslo/v1", "SLO", "UpdatedName", "Updated Name", "{}");
        
        when(repository.findById(1L)).thenReturn(Optional.of(slo));
        when(repository.save(any(OpenSlo.class))).thenReturn(slo);

        OpenSlo result = service.updateRecord(1L, updatedSlo);

        assertThat(result.getName()).isEqualTo("UpdatedName");
        verify(repository, times(1)).findById(1L);
        verify(repository, times(1)).save(any(OpenSlo.class));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentRecord() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateRecord(1L, new OpenSlo()));
    }

    @Test
    void shouldDeleteRecord() {
        when(repository.findById(1L)).thenReturn(Optional.of(slo));
        doNothing().when(repository).delete(slo);

        service.deleteRecord(1L);

        verify(metricRepository, times(1)).deleteBySli(slo);
        verify(metricRepository, times(1)).deleteByDatasource(slo);
        verify(repository, times(1)).delete(slo);
    }

    @Test
    void shouldGetSliReport() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        sli.setId(2L);
        SliMetric metric = new SliMetric(java.time.LocalDateTime.now(), 95.0, sli, null);

        when(repository.findById(2L)).thenReturn(Optional.of(sli));
        when(metricRepository.findBySliOrderByTimestampDesc(sli)).thenReturn(Arrays.asList(metric));

        SliReport report = service.getSliReport(2L);

        assertThat(report).isNotNull();
        assertThat(report.getName()).isEqualTo("TestSLI");
        assertThat(report.getCurrentValue()).isEqualTo(95.0);
        assertThat(report.getRecentMetrics()).hasSize(1);
    }

    @Test
    void shouldThrowExceptionWhenGettingReportForNonSli() {
        when(repository.findById(1L)).thenReturn(Optional.of(slo));

        assertThrows(RuntimeException.class, () -> service.getSliReport(1L));
    }

    @Test
    void shouldGetRecordsByKind() {
        when(repository.findByKind("SLO")).thenReturn(Arrays.asList(slo));

        List<OpenSlo> results = service.getRecordsByKind("SLO");

        assertThat(results).hasSize(1);
        verify(repository, times(1)).findByKind("SLO");
    }

    @Test
    void shouldCreateMetric() {
        SliMetric metric = new SliMetric();
        metric.setValue(99.0); // a value is required and must be within 0 to 100
        when(metricRepository.save(metric)).thenReturn(metric);

        SliMetric created = service.createMetric(metric);

        assertThat(created).isEqualTo(metric);
        verify(metricRepository, times(1)).save(metric);
    }

    @Test
    void shouldGetAllMetrics() {
        when(metricRepository.findAll()).thenReturn(Arrays.asList(new SliMetric()));

        List<SliMetric> results = service.getAllMetrics();

        assertThat(results).hasSize(1);
        verify(metricRepository, times(1)).findAll();
    }

    @Test
    void shouldGetMetricsBySli() {
        OpenSlo sli = new OpenSlo();
        when(metricRepository.findBySliOrderByTimestampDesc(sli)).thenReturn(Arrays.asList(new SliMetric()));

        List<SliMetric> results = service.getMetricsBySli(sli);

        assertThat(results).hasSize(1);
        verify(metricRepository, times(1)).findBySliOrderByTimestampDesc(sli);
    }

    @Test
    void shouldGetServiceReport() {
        OpenSlo serviceRecord = new OpenSlo("openslo/v1", "BusinessService", "TestService", "Test Service", "{}");
        serviceRecord.setId(3L);
        serviceRecord.setSlos(Arrays.asList(slo));

        when(repository.findById(3L)).thenReturn(Optional.of(serviceRecord));

        BusinessServiceReport report = service.getServiceReport(3L);

        assertThat(report).isNotNull();
        assertThat(report.getServiceName()).isEqualTo("TestService");
        assertThat(report.getSloReports()).hasSize(1);
        assertThat(report.getSloReports().get(0).getName()).isEqualTo("TestSLO");
    }
    @Test
    void shouldGetDashboardStats() {
        OpenSlo serviceRecord = new OpenSlo("openslo/v1", "BusinessService", "TestService", "Test Service", "{}");
        OpenSlo sliRecord = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d");
        sloRecord.setSlis(Arrays.asList(sliRecord));
        
        when(repository.findByKind("BusinessService")).thenReturn(Arrays.asList(serviceRecord));
        when(repository.findByKind("SLO")).thenReturn(Arrays.asList(sloRecord));
        
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(Arrays.asList(new SliMetric(null, 98.0, null, null)));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.getTotalBusinessServices()).isEqualTo(1);
        assertThat(stats.getTotalSlos()).isEqualTo(1);
        assertThat(stats.getHealthySlos()).isEqualTo(1);
        assertThat(stats.getWarningSlos()).isEqualTo(0);
        assertThat(stats.getBreachingSlos()).isEqualTo(0);
    }

    @Test
    void shouldGetDashboardStatsWithWarning() {
        OpenSlo serviceRecord = new OpenSlo("openslo/v1", "BusinessService", "TestService", "Test Service", "{}");
        OpenSlo sliRecord = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d");
        sloRecord.setSlis(Arrays.asList(sliRecord));
        
        when(repository.findByKind("BusinessService")).thenReturn(Arrays.asList(serviceRecord));
        when(repository.findByKind("SLO")).thenReturn(Arrays.asList(sloRecord));
        
        // Target 95, value 95.5 -> error budget = (95.5 - 95) / (100 - 95) * 100 = 10%
        // 10% is Warning (0-25)
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(Arrays.asList(new SliMetric(null, 95.5, null, null)));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.getHealthySlos()).isEqualTo(0);
        assertThat(stats.getWarningSlos()).isEqualTo(1);
        assertThat(stats.getBreachingSlos()).isEqualTo(0);
    }

    @Test
    void shouldGetDashboardStatsWithBreaching() {
        OpenSlo serviceRecord = new OpenSlo("openslo/v1", "BusinessService", "TestService", "Test Service", "{}");
        OpenSlo sliRecord = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d");
        sloRecord.setSlis(Arrays.asList(sliRecord));
        
        when(repository.findByKind("BusinessService")).thenReturn(Arrays.asList(serviceRecord));
        when(repository.findByKind("SLO")).thenReturn(Arrays.asList(sloRecord));
        
        // Target 95, value 94 -> error budget = (94 - 95) / (100 - 95) * 100 = -20%
        // -20% is Breaching (<0)
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(Arrays.asList(new SliMetric(null, 94.0, null, null)));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.getHealthySlos()).isEqualTo(0);
        assertThat(stats.getWarningSlos()).isEqualTo(0);
        assertThat(stats.getBreachingSlos()).isEqualTo(1);
    }

    @Test
    void shouldPopulateServiceTransientFields() {
        OpenSlo serviceRecord = new OpenSlo("openslo/v1", "BusinessService", "TestService", "Test Service", "{}");
        OpenSlo sliRecord = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d");
        sloRecord.setSlis(Arrays.asList(sliRecord));
        serviceRecord.setSlos(Arrays.asList(sloRecord));

        when(repository.findAll()).thenReturn(Arrays.asList(serviceRecord));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(Arrays.asList(new SliMetric(null, 98.0, null, null)));

        List<OpenSlo> results = service.getAllRecords();

        assertThat(results).hasSize(1);
        OpenSlo result = results.get(0);
        assertThat(result.getCurrentValue()).isCloseTo(98.0, within(0.001));
        assertThat(result.getStatus()).isEqualTo("Healthy");
        assertThat(result.getErrorBudget()).isNotNull();
    }

    @Test
    void shouldParseTargetFromSpec() {
        String spec = "spec:\n  objectives:\n    - target: 0.999";
        assertThat(service.parseTargetFromSpec(spec)).isEqualTo(99.9);

        String noTargetSpec = "spec:\n  objectives: []";
        assertThat(service.parseTargetFromSpec(noTargetSpec)).isEqualTo(99.0); // Default value

        assertThat(service.parseTargetFromSpec(null)).isEqualTo(99.0);
        assertThat(service.parseTargetFromSpec("spec:\n  objectives:\n    - targetPercent: 99.9")).isEqualTo(99.9);
    }

    @Test
    void shouldReturnNullTargetForInvalidStoredSpec() {
        assertThat(service.parseTargetFromSpec("spec:\n  objectives:\n    - target: 99.9")).isNull();
    }

    @Test
    void shouldGiveIssueScenarioErrorBudgetOf50Percent() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.99");
        sloRecord.setSlis(Arrays.asList(sli));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(new SliMetric(null, 99.5, null, null)));

        com.example.meloslo.dto.SloReport report = service.calculateSloReport(sloRecord);

        assertThat(report.getTarget()).isCloseTo(99.0, within(1e-9));
        assertThat(report.getErrorBudget()).isCloseTo(50.0, within(1e-9));
        assertThat(report.getStatus()).isEqualTo("Healthy");
    }

    @Test
    void shouldReportUnknownForInvalidStoredTargetEvenWithHighValue() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "TestSLI", "Test SLI", "{}");
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 99.9");
        sloRecord.setSlis(Arrays.asList(sli));
        sli.setId(5L);
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(new SliMetric(null, 99.5, null, null)));

        com.example.meloslo.dto.SloReport report = service.calculateSloReport(sloRecord);
        assertThat(report.getStatus()).isEqualTo("Unknown");
        assertThat(report.getTarget()).isNull();
        assertThat(report.getErrorBudget()).isNull();

        SliMetric m = new SliMetric(java.time.LocalDateTime.now(), 99.5, sli, null);
        when(metricRepository.findBySliInAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(Arrays.asList(m));
        List<OpenSlo> list = Arrays.asList(sloRecord);
        service.populateTransientFieldsForList(list);
        assertThat(sloRecord.getStatus()).isEqualTo("Unknown");
        assertThat(sloRecord.getTarget()).isNull();
    }

    @Test
    void shouldSetTargetInListEvenWithoutLinkedSlis() {
        OpenSlo sloRecord = new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "spec:\n  objectives:\n    - target: 0.95");

        service.populateTransientFieldsForList(Arrays.asList(sloRecord));

        assertThat(sloRecord.getTarget()).isCloseTo(95.0, within(1e-9));
    }

    @Test
    void shouldRejectOutOfRangeMetricWithoutSaving() {
        assertThrows(InvalidSliValueException.class,
                () -> service.createMetric(new SliMetric(java.time.LocalDateTime.now(), 150.0, null, null)));
        verify(metricRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidSloTargetsOnCreateAndUpdate() {
        OpenSlo bad = new OpenSlo("openslo/v1", "SLO", "Bad", "Bad", "spec:\n  objectives:\n    - target: 99.9");
        OpenSlo badSecond = new OpenSlo("openslo/v1", "SLO", "Bad2", "Bad2",
                "spec:\n  objectives:\n    - target: 0.99\n    - targetPercent: 100");
        assertThrows(InvalidSliValueException.class, () -> service.createRecord(bad));
        assertThrows(InvalidSliValueException.class, () -> service.createRecord(badSecond));
        assertThrows(InvalidSliValueException.class, () -> service.updateRecord(1L, bad));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotValidateTargetsOfNonSloKinds() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "Sli", "Sli", "spec:\n  objectives:\n    - target: 99.9");
        when(repository.save(sli)).thenReturn(sli);
        assertThat(service.createRecord(sli)).isSameAs(sli);
    }

    @Test
    void shouldParseWindowDaysFromSpec() {
        String spec = "spec:\n  timeWindow:\n    - duration: 30d";
        assertThat(service.parseWindowDaysFromSpec(spec)).isEqualTo(30);

        String smallWindow = "spec:\n  timeWindow:\n    - duration: 7d";
        assertThat(service.parseWindowDaysFromSpec(smallWindow)).isEqualTo(7);

        String invalidWindow = "spec:\n  timeWindow:\n    - duration: abc";
        assertThat(service.parseWindowDaysFromSpec(invalidWindow)).isEqualTo(30); // Default

        assertThat(service.parseWindowDaysFromSpec(null)).isEqualTo(30);
    }
}
