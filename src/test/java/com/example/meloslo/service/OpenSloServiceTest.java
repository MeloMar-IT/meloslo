package com.example.meloslo.service;

import com.example.meloslo.dto.DashboardStats;
import com.example.meloslo.dto.BusinessServiceReport;
import com.example.meloslo.dto.SliReport;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.User;
import java.time.LocalDateTime;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
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

    @Mock
    private AlertUrlValidator alertUrlValidator;

    private OpenSloService service;

    private OpenSlo slo;

    @BeforeEach
    void setUp() {
        service = new OpenSloService(repository, metricRepository,
                new DepartmentAccessService(userRepository), new OpenSloRedactor(), alertUrlValidator);
        slo =new OpenSlo("openslo/v1", "SLO", "TestSLO", "Test SLO", "{}");
        slo.setId(1L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String departments, String role) {
        if (departments != null || !"ROLE_ADMIN".equals(role)) {
            lenient().when(userRepository.findByUsername(username))
                    .thenReturn(Optional.of(new User(username, "pass", username + "@test.com", departments)));
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "pass", List.of(new SimpleGrantedAuthority(role))));
    }

    private OpenSlo record(Long id, String kind, String name, String department) {
        OpenSlo r = new OpenSlo("openslo/v1", kind, name, name + " display", "{}");
        r.setId(id);
        r.setDepartment(department);
        return r;
    }

    @Test
    void userCanOnlyGetRecordsOfOwnDepartmentsById() {
        loginAs("testuser", "Finance,Engineering", "ROLE_USER");
        when(repository.findById(10L)).thenReturn(Optional.of(record(10L, "SLO", "fin", " finance ")));
        when(repository.findById(11L)).thenReturn(Optional.of(record(11L, "SLO", "hr", "HR")));
        when(repository.findById(12L)).thenReturn(Optional.of(record(12L, "SLO", "none", null)));

        assertThat(service.getRecordById(10L)).isPresent();
        assertThat(service.getRecordById(11L)).isEmpty();
        assertThat(service.getRecordById(12L)).isEmpty();
    }

    @Test
    void adminCanGetRecordWithoutDepartment() {
        loginAs("admin", null, "ROLE_ADMIN");
        when(repository.findById(12L)).thenReturn(Optional.of(record(12L, "SLO", "none", null)));

        assertThat(service.getRecordById(12L)).isPresent();
    }

    @Test
    void userWithoutDepartmentsSeesNothing() {
        loginAs("nodept", null, "ROLE_USER");
        when(repository.findAll()).thenReturn(Arrays.asList(record(10L, "SLO", "fin", "Finance")));
        when(repository.findById(10L)).thenReturn(Optional.of(record(10L, "SLO", "fin", "Finance")));

        assertThat(service.getAllRecords()).isEmpty();
        assertThat(service.getRecordById(10L)).isEmpty();
        assertThrows(AccessDeniedException.class, () -> service.createRecord(record(null, "SLO", "n", "Finance")));
    }

    @Test
    void listExcludesRecordsWithoutDepartmentForUsers() {
        loginAs("testuser", "Finance", "ROLE_USER");
        when(repository.findAll()).thenReturn(Arrays.asList(
                record(1L, "SLO", "fin", "Finance"), record(2L, "SLO", "none", null), record(3L, "SLO", "blank", " ")));

        assertThat(service.getAllRecords()).extracting(OpenSlo::getName).containsExactly("fin");
    }

    @Test
    void dashboardStatsAreZeroForUserWithoutDepartments() {
        loginAs("nodept", "  ", "ROLE_USER");
        when(repository.findByKind("BusinessService")).thenReturn(Arrays.asList(record(1L, "BusinessService", "s", "Finance")));
        when(repository.findByKind("SLO")).thenReturn(Arrays.asList(record(2L, "SLO", "o", "Finance")));

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.getTotalBusinessServices()).isZero();
        assertThat(stats.getTotalSlos()).isZero();
        assertThat(stats.getHealthySlos() + stats.getWarningSlos() + stats.getBreachingSlos()).isZero();
    }

    @Test
    void linkedRecordsOfOtherDepartmentsAreRedactedWithoutTouchingTheEntity() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo infraSli = record(20L, "SLI", "infra-sli", "Infrastructure");
        OpenSlo finSli = record(21L, "SLI", "fin-sli", "Finance");
        OpenSlo infraAlert = record(22L, "AlertingSource", "infra-alert", "Infrastructure");
        OpenSlo finSlo = record(23L, "SLO", "fin-slo", "Finance");
        finSlo.setSlis(new ArrayList<>(Arrays.asList(infraSli, finSli)));
        finSlo.setAlertingSource(infraAlert);
        when(repository.findById(23L)).thenReturn(Optional.of(finSlo));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(new ArrayList<>());

        OpenSlo result = service.getRecordById(23L).orElseThrow();

        assertThat(result.getSlis()).extracting(OpenSlo::getName).containsExactly("fin-sli");
        assertThat(result.getAlertingSource()).isNull();
        assertThat(finSlo.getSlis()).hasSize(2);
        assertThat(finSlo.getAlertingSource()).isSameAs(infraAlert);
        verify(metricRepository, never()).findBySliAndTimestampAfterOrderByTimestampDesc(eq(infraSli), any());
    }

    @Test
    void reportsOnOtherDepartmentsRecordsAreNotFound() {
        loginAs("testuser", "Finance", "ROLE_USER");
        when(repository.findById(30L)).thenReturn(Optional.of(record(30L, "SLO", "hr-slo", "HR")));
        when(repository.findById(31L)).thenReturn(Optional.of(record(31L, "SLI", "hr-sli", "HR")));
        when(repository.findById(32L)).thenReturn(Optional.of(record(32L, "BusinessService", "hr-svc", "HR")));

        assertThrows(RuntimeException.class, () -> service.getSloReport(30L));
        assertThrows(RuntimeException.class, () -> service.getSliReport(31L));
        assertThrows(RuntimeException.class, () -> service.getServiceReport(32L));
        verifyNoInteractions(metricRepository);
    }

    @Test
    void serviceReportIncludesOnlyVisibleChildren() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo svc = record(40L, "BusinessService", "svc", "Finance");
        svc.setSlos(new ArrayList<>(Arrays.asList(
                record(41L, "SLO", "fin-child", "Finance"), record(42L, "SLO", "hr-child", "HR"))));
        when(repository.findById(40L)).thenReturn(Optional.of(svc));

        BusinessServiceReport report = service.getServiceReport(40L);

        assertThat(report.getSloReports()).extracting(r -> r.getName()).containsExactly("fin-child");
        assertThat(service.getAccessibleSlos(svc)).extracting(OpenSlo::getName).containsExactly("fin-child");
    }

    @Test
    void businessServiceAggregatesOnlyVisibleChildren() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo svc = record(50L, "BusinessService", "svc", "Finance");
        OpenSlo hrChild = record(52L, "SLO", "hr-child", "HR");
        OpenSlo hrSli = record(53L, "SLI", "hr-sli", "HR");
        hrChild.setSlis(new ArrayList<>(Arrays.asList(hrSli)));
        svc.setSlos(new ArrayList<>(Arrays.asList(hrChild)));
        when(repository.findById(50L)).thenReturn(Optional.of(svc));

        OpenSlo result = service.getRecordById(50L).orElseThrow();

        assertThat(result.getSlos()).isEmpty();
        // No visible child SLO means no data: never a fake 100% / Healthy (issue 39)
        assertThat(result.getCurrentValue()).isNull();
        assertThat(result.getErrorBudget()).isNull();
        assertThat(result.getStatus()).isEqualTo("No data");
        verifyNoInteractions(metricRepository);
    }

    @Test
    void userCannotCreateInForeignOrMissingDepartment() {
        loginAs("testuser", "Finance", "ROLE_USER");

        assertThrows(AccessDeniedException.class, () -> service.createRecord(record(null, "SLO", "a", "HR")));
        assertThrows(AccessDeniedException.class, () -> service.createRecord(record(null, "SLO", "b", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void userCanCreateInOwnDepartmentIgnoringCase() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo input = record(null, "SLO", "a", "finance");
        when(repository.save(input)).thenReturn(input);

        OpenSlo created = service.createRecord(input);

        assertThat(created.getDepartment()).isEqualTo("finance");
        verify(repository).save(input);
    }

    @Test
    void createWithExistingIdIsRejected() {
        loginAs("admin", null, "ROLE_ADMIN");

        assertThrows(BadRequestException.class, () -> service.createRecord(record(5L, "SLO", "a", "Finance")));
        verify(repository, never()).save(any());
    }

    @Test
    void updateOfOtherDepartmentsRecordIsNotFound() {
        loginAs("testuser", "Finance", "ROLE_USER");
        when(repository.findById(60L)).thenReturn(Optional.of(record(60L, "SLO", "hr", "HR")));

        assertThrows(RuntimeException.class, () -> service.updateRecord(60L, record(null, "SLO", "hr", "Finance")));
        verify(repository, never()).save(any());
    }

    @Test
    void updateCannotMoveRecordToForeignDepartment() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo existing = record(61L, "SLO", "fin", "Finance");
        when(repository.findById(61L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> service.updateRecord(61L, record(null, "SLO", "renamed", "Infrastructure")));

        assertThat(existing.getDepartment()).isEqualTo("Finance");
        assertThat(existing.getName()).isEqualTo("fin");
        verify(repository, never()).save(any());
    }

    private OpenSlo source(Long id, String url) {
        OpenSlo s = record(id, "AlertingSource", "src", "Finance");
        s.setAlertUrl(url);
        return s;
    }

    @Test
    void createValidatesAlertUrlAndDoesNotSaveWhenRefused() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo s = source(null, "http://127.0.0.1/hook");
        doThrow(new BadRequestException("Alert URL points to a private or internal address"))
                .when(alertUrlValidator).validate("http://127.0.0.1/hook");

        assertThrows(BadRequestException.class, () -> service.createRecord(s));

        verify(repository, never()).save(any());
    }

    @Test
    void updateWithMaskOfStoredUrlKeepsStoredUrlWithoutValidation() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = source(70L, "https://hooks.example.test/secret/path");
        when(repository.findById(70L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.updateRecord(70L, source(null, "https://hooks.example.test/****"));

        assertThat(existing.getAlertUrl()).isEqualTo("https://hooks.example.test/secret/path");
        verify(alertUrlValidator, never()).validate(any());
    }

    @Test
    void updateWithNewUrlValidatesAndSetsIt() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = source(71L, "https://hooks.example.test/secret/path");
        when(repository.findById(71L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service.updateRecord(71L, source(null, "https://93.184.216.34/new"));

        verify(alertUrlValidator).validate("https://93.184.216.34/new");
        assertThat(existing.getAlertUrl()).isEqualTo("https://93.184.216.34/new");
    }

    @Test
    void updateWithRefusedUrlThrowsAndDoesNotSave() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = source(72L, "https://hooks.example.test/secret/path");
        when(repository.findById(72L)).thenReturn(Optional.of(existing));
        doThrow(new BadRequestException("refused")).when(alertUrlValidator).validate("http://10.0.0.5/x");

        assertThrows(BadRequestException.class, () -> service.updateRecord(72L, source(null, "http://10.0.0.5/x")));

        assertThat(existing.getAlertUrl()).isEqualTo("https://hooks.example.test/secret/path");
        verify(repository, never()).save(any());
    }

    @Test
    void updateWithMaskOfAnotherHostIsValidatedAndNotKept() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = source(73L, "https://hooks.example.test/secret/path");
        when(repository.findById(73L)).thenReturn(Optional.of(existing));
        doThrow(new BadRequestException("Enter the full alert URL"))
                .when(alertUrlValidator).validate("https://other.example.test/****");

        assertThrows(BadRequestException.class,
                () -> service.updateRecord(73L, source(null, "https://other.example.test/****")));

        assertThat(existing.getAlertUrl()).isEqualTo("https://hooks.example.test/secret/path");
        verify(repository, never()).save(any());
    }

    @Test
    void updateCanMoveRecordBetweenOwnDepartments() {
        loginAs("testuser", "Finance,Engineering", "ROLE_USER");
        OpenSlo existing = record(62L, "SLO", "fin", "Finance");
        when(repository.findById(62L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        OpenSlo result = service.updateRecord(62L, record(null, "SLO", "fin", "Engineering"));

        assertThat(result.getDepartment()).isEqualTo("Engineering");
        verify(repository).save(existing);
    }

    @Test
    void deleteOfOtherDepartmentsOrMissingRecordIsNotFound() {
        loginAs("testuser", "Finance", "ROLE_USER");
        when(repository.findById(70L)).thenReturn(Optional.of(record(70L, "SLO", "hr", "HR")));
        when(repository.findById(71L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.deleteRecord(70L));
        assertThrows(RuntimeException.class, () -> service.deleteRecord(71L));
        verify(repository, never()).delete(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void deleteOfSliUsedByAnSloIsConflictAndChangesNothing() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo sli = record(90L, "SLI", "used-sli", null);
        when(repository.findById(90L)).thenReturn(Optional.of(sli));
        when(repository.countSlosUsingSli(90L)).thenReturn(1L);

        ConflictException e = assertThrows(ConflictException.class, () -> service.deleteRecord(90L));

        assertThat(e.getMessage()).contains("used-sli").contains("1 SLO");
        verify(repository, never()).delete(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void deleteOfBusinessServiceWithSloIsConflict() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo bs = record(91L, "BusinessService", "bs", null);
        when(repository.findById(91L)).thenReturn(Optional.of(bs));
        when(repository.countByService(bs)).thenReturn(2L);

        assertThrows(ConflictException.class, () -> service.deleteRecord(91L));
        verify(repository, never()).delete(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void deleteOfDataSourceWithSliIsConflict() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo ds = record(92L, "DataSource", "ds", null);
        when(repository.findById(92L)).thenReturn(Optional.of(ds));
        when(repository.countByDatasource(ds)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.deleteRecord(92L));
        verify(repository, never()).delete(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void updateWithNameOfAnotherRecordIsConflict() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = record(100L, "SLO", "a", null);
        when(repository.findById(100L)).thenReturn(Optional.of(existing));
        when(repository.findByName("b")).thenReturn(Optional.of(record(101L, "SLO", "b", null)));

        assertThrows(ConflictException.class, () -> service.updateRecord(100L, record(null, "SLO", "b", null)));

        assertThat(existing.getName()).isEqualTo("a");
        verify(repository, never()).save(any());
    }

    @Test
    void updateKeepingItsOwnNameIsAllowed() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = record(102L, "SLO", "same", null);
        when(repository.findById(102L)).thenReturn(Optional.of(existing));
        when(repository.findByName("same")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.updateRecord(102L, record(null, "SLO", "same", null)).getName()).isEqualTo("same");
    }

    @Test
    void createWithExistingNameIsConflict() {
        loginAs("admin", null, "ROLE_ADMIN");
        when(repository.findByName("dup")).thenReturn(Optional.of(record(103L, "SLO", "dup", null)));

        assertThrows(ConflictException.class, () -> service.createRecord(record(null, "SLO", "dup", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void updateOfMissingRecordIsResourceNotFound() {
        loginAs("admin", null, "ROLE_ADMIN");
        when(repository.findById(104L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.updateRecord(104L, record(null, "SLO", "x", null)));
    }

    @Test
    void createClearsSystemManagedFields() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo input = record(null, "SLO", "fresh", null);
        input.setLastRefreshTime(LocalDateTime.now());
        input.setLastAlertTime(LocalDateTime.now());
        input.setLastFetchFailureTime(LocalDateTime.now());
        input.setLastFetchError("boom");
        when(repository.save(input)).thenReturn(input);

        OpenSlo created = service.createRecord(input);

        assertThat(created.getLastRefreshTime()).isNull();
        assertThat(created.getLastAlertTime()).isNull();
        assertThat(created.getLastFetchFailureTime()).isNull();
        assertThat(created.getLastFetchError()).isNull();
    }

    @Test
    void updateIgnoresLastRefreshTimeFromRequest() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo existing = record(105L, "SLO", "keep", null);
        when(repository.findById(105L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        OpenSlo update = record(null, "SLO", "keep", null);
        update.setLastRefreshTime(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.updateRecord(105L, update);

        assertThat(existing.getLastRefreshTime()).isNull();
    }

    @Test
    void businessServiceSloLinksWithNullEntryAreRejectedBeforeAnySave() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo bs = record(106L, "BusinessService", "bs-links", null);
        when(repository.findById(106L)).thenReturn(Optional.of(bs));
        when(repository.save(bs)).thenReturn(bs);
        OpenSlo update = record(null, "BusinessService", "bs-links", null);
        update.setSlos(Arrays.asList((OpenSlo) null));

        assertThrows(BadRequestException.class, () -> service.updateRecord(106L, update));
        verify(repository, never()).save(argThat(r -> r != bs));
    }

    @Test
    void businessServiceWithNullSlosIsRejected() {
        loginAs("admin", null, "ROLE_ADMIN");
        OpenSlo input = record(null, "BusinessService", "bs-null", null);
        input.setSlos(null);
        when(repository.save(input)).thenReturn(input);

        assertThrows(BadRequestException.class, () -> service.createRecord(input));
    }

    @Test
    void userCannotDeleteAlertingSourceUsedByForeignSlo() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo source = record(80L, "AlertingSource", "fin-alert", "Finance");
        OpenSlo foreignSlo = record(81L, "SLO", "hr-slo", "HR");
        foreignSlo.setAlertingSource(source);
        when(repository.findById(80L)).thenReturn(Optional.of(source));
        when(repository.findByKind("SLO")).thenReturn(List.of(foreignSlo));

        assertThrows(AccessDeniedException.class, () -> service.deleteRecord(80L));

        assertThat(foreignSlo.getAlertingSource()).isSameAs(source);
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
        verifyNoInteractions(metricRepository);
    }

    @Test
    void userDeletingAlertingSourceUnlinksOnlyOwnSlos() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo source = record(82L, "AlertingSource", "fin-alert", "Finance");
        OpenSlo ownSlo = record(83L, "SLO", "fin-slo", "Finance");
        ownSlo.setAlertingSource(source);
        when(repository.findById(82L)).thenReturn(Optional.of(source));
        when(repository.findByKind("SLO")).thenReturn(List.of(ownSlo));

        service.deleteRecord(82L);

        assertThat(ownSlo.getAlertingSource()).isNull();
        verify(repository).save(ownSlo);
        verify(repository).delete(source);
    }

    @Test
    void sliReportMetricsDoNotExposeForeignDatasource() {
        loginAs("testuser", "Finance", "ROLE_USER");
        OpenSlo sli = record(90L, "SLI", "fin-sli", "Finance");
        OpenSlo infraDs = record(91L, "DataSource", "infra-ds", "Infrastructure");
        SliMetric metric = new SliMetric(LocalDateTime.now(), 0.9, sli, infraDs);
        when(repository.findById(90L)).thenReturn(Optional.of(sli));
        when(metricRepository.findBySliOrderByTimestampDesc(sli)).thenReturn(List.of(metric));

        SliReport report = service.getSliReport(90L);

        assertThat(report.getRecentMetrics()).hasSize(1);
        assertThat(report.getRecentMetrics().get(0).getSli().getName()).isEqualTo("fin-sli");
        assertThat(report.getRecentMetrics().get(0).getDatasource()).isNull();
        assertThat(metric.getDatasource()).isSameAs(infraDs);
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
    void userNamedAdminWithoutAdminRoleIsStillFilteredByDepartment() {
        User user = new User("admin", "pass", "a@test.com", "Finance");
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.setContext(new org.springframework.security.core.context.SecurityContextImpl(auth));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        OpenSlo financeSlo = new OpenSlo("openslo/v1", "SLO", "FinanceSLO", "Finance SLO", "{}");
        financeSlo.setDepartment("Finance");
        OpenSlo hrSlo = new OpenSlo("openslo/v1", "SLO", "HRSLO", "HR SLO", "{}");
        hrSlo.setDepartment("HR");
        when(repository.findAll()).thenReturn(Arrays.asList(financeSlo, hrSlo));

        List<OpenSlo> results = service.getAllRecords();

        assertThat(results).extracting(OpenSlo::getName).containsExactly("FinanceSLO");
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

    private static final String SPEC_95 = "spec:\n  objectives:\n    - target: 0.95\n  timeWindow:\n    - duration: 30d";

    private OpenSlo sloWithSli(String name) {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", name + "-sli", name + " SLI", "{}");
        OpenSlo s = new OpenSlo("openslo/v1", "SLO", name, name, SPEC_95);
        s.setSlis(Arrays.asList(sli));
        return s;
    }

    @Test
    void sloWithoutSamplesReportsNoData() {
        OpenSlo s = sloWithSli("Empty");
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(List.of());

        var report = service.calculateSloReport(s);

        assertThat(report.getStatus()).isEqualTo("No data");
        assertThat(report.getCurrentValue()).isNull();
        assertThat(report.getErrorBudget()).isNull();
    }

    @Test
    void dashboardStatsCountNoDataSeparately() {
        OpenSlo s = sloWithSli("Empty");
        when(repository.findByKind("BusinessService")).thenReturn(List.of());
        when(repository.findByKind("SLO")).thenReturn(List.of(s));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any())).thenReturn(List.of());

        DashboardStats stats = service.getDashboardStats();

        assertThat(stats.getNoDataSlos()).isEqualTo(1);
        assertThat(stats.getBreachingSlos()).isEqualTo(0);
        assertThat(stats.getHealthySlos()).isEqualTo(0);
        assertThat(stats.getWarningSlos()).isEqualTo(0);
    }

    @Test
    void getAllRecordsGivesNoDataForSloWithoutSlisAndWithoutMetrics() {
        SecurityContextHolder.clearContext();
        OpenSlo noSlis = new OpenSlo("openslo/v1", "SLO", "NoSlis", "No SLIs", SPEC_95);
        OpenSlo noMetrics = sloWithSli("NoMetrics");
        when(repository.findAll()).thenReturn(Arrays.asList(noSlis, noMetrics));

        List<OpenSlo> results = service.getAllRecords();

        for (OpenSlo r : results) {
            assertThat(r.getStatus()).isEqualTo("No data");
            assertThat(r.getCurrentValue()).isNull();
            assertThat(r.getErrorBudget()).isNull();
        }
    }

    @Test
    void businessServiceWithoutDataOrSlosIsNoData() {
        SecurityContextHolder.clearContext();
        OpenSlo withEmptySlo = new OpenSlo("openslo/v1", "BusinessService", "Svc1", "Svc1", "{}");
        withEmptySlo.setSlos(Arrays.asList(sloWithSli("Empty")));
        OpenSlo withoutSlos = new OpenSlo("openslo/v1", "BusinessService", "Svc2", "Svc2", "{}");
        when(repository.findAll()).thenReturn(Arrays.asList(withEmptySlo, withoutSlos));

        List<OpenSlo> results = service.getAllRecords();

        for (OpenSlo r : results) {
            assertThat(r.getStatus()).isEqualTo("No data");
            assertThat(r.getCurrentValue()).isNull();
            assertThat(r.getErrorBudget()).isNull();
        }
    }

    @Test
    void businessServiceUsesOnlySlosWithData() {
        SecurityContextHolder.clearContext();
        OpenSlo withData = sloWithSli("WithData");
        OpenSlo withoutData = sloWithSli("WithoutData");
        OpenSlo svc = new OpenSlo("openslo/v1", "BusinessService", "Svc", "Svc", "{}");
        svc.setSlos(Arrays.asList(withData, withoutData));
        when(repository.findAll()).thenReturn(Arrays.asList(svc));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(eq(withData.getSlis().get(0)), any()))
                .thenReturn(Arrays.asList(new SliMetric(null, 98.0, null, null)));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(eq(withoutData.getSlis().get(0)), any()))
                .thenReturn(List.of());

        OpenSlo result = service.getAllRecords().get(0);

        assertThat(result.getCurrentValue()).isCloseTo(98.0, within(0.001));
        assertThat(result.getStatus()).isEqualTo("Healthy");
    }

    @Test
    void historicalValuesAreNullForDaysWithoutSamples() {
        OpenSlo s = sloWithSli("Sparse");
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(new SliMetric(java.time.LocalDateTime.now().minusDays(1), 98.0, null, null)));

        List<Double> history = service.calculateSloReport(s).getHistoricalSloValues();

        assertThat(history).hasSize(30);
        assertThat(history.get(28)).isCloseTo(98.0, within(0.001));
        assertThat(history.stream().filter(java.util.Objects::isNull).count()).isEqualTo(29);
    }

    @Test
    void historicalValuesUseAllSamplesAcrossSlisNotTheCappedList() {
        OpenSlo sliA = new OpenSlo("openslo/v1", "SLI", "A", "A", "{}");
        OpenSlo sliB = new OpenSlo("openslo/v1", "SLI", "B", "B", "{}");
        OpenSlo s = new OpenSlo("openslo/v1", "SLO", "Multi", "Multi", SPEC_95);
        s.setSlis(Arrays.asList(sliA, sliB));
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

        // 50 samples of 50 on SLI A come first, then 10 samples of 100 on SLI B.
        // The first 50 entries alone average 50; all 60 average 58.33.
        List<SliMetric> a = new ArrayList<>();
        for (int i = 0; i < 50; i++) a.add(new SliMetric(yesterday.withSecond(i), 50.0, sliA, null));
        List<SliMetric> b = new ArrayList<>();
        for (int i = 0; i < 10; i++) b.add(new SliMetric(yesterday.withSecond(i), 100.0, sliB, null));
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(eq(sliA), any())).thenReturn(a);
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(eq(sliB), any())).thenReturn(b);

        var report = service.calculateSloReport(s);

        assertThat(report.getRecentMetrics()).hasSize(50); // the display list is capped
        assertThat(report.getHistoricalSloValues().get(28)).isCloseTo((50 * 50.0 + 10 * 100.0) / 60, within(0.001));
    }

    @Test
    void historicalValuesKeepTrailingNullsWhenLatestSampleIsOld() {
        OpenSlo s = sloWithSli("Stale");
        when(metricRepository.findBySliAndTimestampAfterOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(new SliMetric(LocalDateTime.now().minusDays(5), 98.0, null, null)));

        List<Double> history = service.calculateSloReport(s).getHistoricalSloValues();

        assertThat(history).hasSize(30);
        assertThat(history.get(24)).isCloseTo(98.0, within(0.001)); // 5 days ago
        for (int i = 25; i < 30; i++) {
            assertThat(history.get(i)).isNull(); // the last 5 days, up to today, stay gaps
        }
    }

    @Test
    void sliReportWithoutMetricsHasNullCurrentValue() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "EmptySLI", "Empty SLI", "{}");
        sli.setId(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(sli));
        when(metricRepository.findBySliOrderByTimestampDesc(sli)).thenReturn(List.of());

        assertThat(service.getSliReport(9L).getCurrentValue()).isNull();
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
