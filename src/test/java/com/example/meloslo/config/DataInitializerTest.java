package com.example.meloslo.config;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {

    @Mock
    private OpenSloRepository openSloRepository;

    @Mock
    private MetricRepository metricRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dataInitializer = new DataInitializer();
    }

    private CommandLineRunner liveRunner() {
        ReflectionTestUtils.setField(dataInitializer, "datasourceMode", "live");
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        return dataInitializer.initDatabase(openSloRepository, metricRepository, userRepository, passwordEncoder);
    }

    private void verifyNoDemoDataTouched() {
        verifyNoInteractions(openSloRepository);
        // Only the metric scale migration may touch the metric repository
        verify(metricRepository).convertLegacyFractionValues();
        verify(metricRepository).markLegacyValuesAsPercent();
        verifyNoMoreInteractions(metricRepository);
        verify(userRepository, never()).deleteAll();
        verify(userRepository, never()).save(argThat(user -> "testuser".equals(user.getUsername())));
    }

    @Test
    void shouldMigrateLegacyMetricScaleBeforeSeedingInLiveMode() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(metricRepository.convertLegacyFractionValues()).thenReturn(3);
        when(metricRepository.markLegacyValuesAsPercent()).thenReturn(1);

        liveRunner().run();

        InOrder order = inOrder(metricRepository, userRepository);
        order.verify(metricRepository).convertLegacyFractionValues();
        order.verify(metricRepository).markLegacyValuesAsPercent();
        order.verify(userRepository, atLeastOnce()).findByUsername("admin");
    }

    @Test
    void shouldFailStartupAndSeedNothingWhenMigrationFails() {
        ReflectionTestUtils.setField(dataInitializer, "datasourceMode", "test");
        when(metricRepository.convertLegacyFractionValues()).thenThrow(new IllegalStateException("db down"));

        CommandLineRunner runner = dataInitializer.initDatabase(openSloRepository, metricRepository, userRepository, passwordEncoder);

        assertThrows(IllegalStateException.class, runner::run);
        verify(metricRepository, never()).markLegacyValuesAsPercent();
        verify(userRepository, never()).save(any());
        verify(openSloRepository, never()).save(any());
    }

    @Test
    void liveModeCreatesAdminWithAdminRole() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        liveRunner().run();

        verify(userRepository).save(argThat(u -> "admin".equals(u.getUsername()) && u.getRole() == Role.ADMIN));
    }

    @Test
    void backfillAssignsRolesAndFlagsAdminInLiveMode() throws Exception {
        User admin = new User("admin", "h", "a@example.com", null);
        admin.setRole(null);
        User bob = new User("bob", "h", "b@example.com", null);
        bob.setRole(null);
        User carol = new User("carol", "h", "c@example.com", null, Role.ADMIN);
        when(userRepository.findAll()).thenReturn(List.of(admin, bob, carol));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        liveRunner().run();

        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isPasswordChangeRequired());
        assertEquals(Role.USER, bob.getRole());
        assertFalse(bob.isPasswordChangeRequired());
        assertEquals(Role.ADMIN, carol.getRole());
        verify(userRepository, never()).save(carol);
    }

    @Test
    void backfillDoesNotFlagAdminInTestMode() throws Exception {
        User admin = new User("admin", "h", "a@example.com", null);
        admin.setRole(null);
        when(userRepository.findAll()).thenReturn(List.of(admin));
        CommandLineRunner runner = testRunner(false);
        nonEmptyDatabase();

        runner.run();

        assertEquals(Role.ADMIN, admin.getRole());
        assertFalse(admin.isPasswordChangeRequired());
    }

    @Test
    void shouldMigrateNormallyOnRetryAfterFailure() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(metricRepository.convertLegacyFractionValues())
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn(2);

        CommandLineRunner runner = liveRunner();
        assertThrows(IllegalStateException.class, runner::run);

        runner.run();

        verify(metricRepository, times(2)).convertLegacyFractionValues();
        verify(metricRepository, times(1)).markLegacyValuesAsPercent();
    }

    @Test
    void liveModeCreatesOnlyAdminWithRandomPassword() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        CommandLineRunner runner = liveRunner();

        runner.run();

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(password.capture());
        assertThat(password.getValue()).isNotEqualTo("admin").isNotEqualTo("testuser");
        assertThat(password.getValue().length()).isGreaterThanOrEqualTo(20);
        verify(userRepository, times(1)).save(any(User.class));
        verify(userRepository).save(argThat(user -> "admin".equals(user.getUsername())));
        verifyNoDemoDataTouched();
    }

    @Test
    void liveModeUsesConfiguredPassword() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "initialAdminPassword", "S3cret-From-Env-42");
        CommandLineRunner runner = liveRunner();

        runner.run();

        verify(passwordEncoder).encode("S3cret-From-Env-42");
        verify(userRepository, times(1)).save(argThat(user -> "admin".equals(user.getUsername())));
        verifyNoDemoDataTouched();
    }

    @Test
    void liveModeBlankConfiguredPasswordFallsBackToRandom() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "initialAdminPassword", "   ");
        CommandLineRunner runner = liveRunner();

        runner.run();

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(password.capture());
        assertThat(password.getValue().isBlank()).isFalse();
        assertThat(password.getValue().length()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void liveModeWithExistingAdminCreatesNoUsers() throws Exception {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "hash", "a@b.c", null)));
        CommandLineRunner runner = liveRunner();

        runner.run();

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
        verifyNoDemoDataTouched();
    }

    @Test
    void randomPasswordsDiffer() {
        assertThat(dataInitializer.resolveInitialAdminPassword())
            .isNotEqualTo(dataInitializer.resolveInitialAdminPassword());
    }

    private CommandLineRunner testRunner(boolean seed) {
        ReflectionTestUtils.setField(dataInitializer, "datasourceMode", "test");
        ReflectionTestUtils.setField(dataInitializer, "seedRequested", seed);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        lenient().when(openSloRepository.save(any(OpenSlo.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(openSloRepository.findByName(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        return dataInitializer.initDatabase(openSloRepository, metricRepository, userRepository, passwordEncoder);
    }

    private void nonEmptyDatabase() {
        when(openSloRepository.count()).thenReturn(20L);
        when(userRepository.count()).thenReturn(3L);
    }

    private void verifyNothingDeleted() {
        verify(openSloRepository, never()).deleteAll();
        verify(openSloRepository, never()).delete(any());
        verify(openSloRepository, never()).deleteById(any());
        verify(metricRepository, never()).deleteAll();
        verify(metricRepository, never()).deleteBySli(any());
        verify(userRepository, never()).deleteAll();
    }

    private void verifyNothingSaved() {
        verify(openSloRepository, never()).save(any());
        verify(openSloRepository, never()).saveAll(any());
        verify(metricRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    private static String kindOf(String name) {
        if ("payment-service".equals(name) || "engineering-core".equals(name)) return "BusinessService";
        if ("prometheus-prod".equals(name)) return "DataSource";
        if (name.endsWith("-sli")) return "SLI";
        if (name.endsWith("-alerts") || "slack-notifications".equals(name)) return "AlertingSource";
        return "SLO";
    }

    private static OpenSlo existingRecord(String name, String kind) {
        OpenSlo existing = new OpenSlo();
        existing.setName(name);
        existing.setKind(kind);
        return existing;
    }

    private void existingRecordsExcept(String... missing) {
        java.util.Set<String> skip = java.util.Set.of(missing);
        for (String name : DataInitializer.SEED_RECORD_NAMES) {
            if (!skip.contains(name)) {
                when(openSloRepository.findByName(name)).thenReturn(Optional.of(existingRecord(name, kindOf(name))));
            }
        }
    }

    @Test
    void seedFlagSkipsDemoRecordsThatConflictWithExistingKind() throws Exception {
        CommandLineRunner runner = testRunner(true);
        nonEmptyDatabase();
        // Every seed name exists with the right kind, except a user-created SLI called payment-service
        // and the three SLOs that depend on it, which are missing.
        existingRecordsExcept("web-latency", "api-availability", "db-errors", "payment-service");
        when(openSloRepository.findByName("payment-service")).thenReturn(Optional.of(existingRecord("payment-service", "SLI")));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "h", "a@b.c", null)));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User("testuser", "h", "t@b.c", null)));

        runner.run();

        // The conflicting record is kept unchanged and its dependent SLOs are not created against it.
        verifyNothingSaved();
        verifyNothingDeleted();
    }

    @Test
    void testModeSeedsEmptyDatabaseWithoutDeleting() throws Exception {
        CommandLineRunner runner = testRunner(false);
        when(openSloRepository.count()).thenReturn(0L);
        when(userRepository.count()).thenReturn(0L);

        runner.run();

        ArgumentCaptor<OpenSlo> records = ArgumentCaptor.forClass(OpenSlo.class);
        verify(openSloRepository, times(16)).save(records.capture());
        assertThat(records.getAllValues()).extracting(OpenSlo::getName)
            .containsExactlyInAnyOrderElementsOf(DataInitializer.SEED_RECORD_NAMES);
        ArgumentCaptor<User> users = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(users.capture());
        assertThat(users.getAllValues()).extracting(User::getUsername).containsExactly("admin", "testuser");
        verify(metricRepository, times(150)).save(any());
        verifyNothingDeleted();
    }

    @Test
    void testModeWithExistingDataSkipsSeeding() throws Exception {
        CommandLineRunner runner = testRunner(false);
        nonEmptyDatabase();

        runner.run();

        verifyNothingSaved();
        verifyNothingDeleted();
        verify(metricRepository, never()).countBySli(any());
    }

    @Test
    void testModeMissingSeedRecordWithoutFlagChangesNothing() throws Exception {
        CommandLineRunner runner = testRunner(false);
        nonEmptyDatabase();
        existingRecordsExcept("latency-sli");

        runner.run();

        verifyNothingSaved();
        verifyNothingDeleted();
    }

    @Test
    void seedFlagAddsOnlyMissingRecords() throws Exception {
        CommandLineRunner runner = testRunner(true);
        nonEmptyDatabase();
        existingRecordsExcept("discord-alerts", "latency-sli");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "h", "a@b.c", null)));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User("testuser", "h", "t@b.c", null)));

        runner.run();

        ArgumentCaptor<OpenSlo> records = ArgumentCaptor.forClass(OpenSlo.class);
        verify(openSloRepository, times(2)).save(records.capture());
        assertThat(records.getAllValues()).extracting(OpenSlo::getName)
            .containsExactlyInAnyOrder("discord-alerts", "latency-sli");
        ArgumentCaptor<com.example.meloslo.model.SliMetric> metrics =
            ArgumentCaptor.forClass(com.example.meloslo.model.SliMetric.class);
        verify(metricRepository, times(30)).save(metrics.capture());
        assertThat(metrics.getAllValues()).allSatisfy(m -> assertThat(m.getSli().getName()).isEqualTo("latency-sli"));
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
        verifyNothingDeleted();
    }

    @Test
    void seedFlagRecreatesMissingTestuserOnly() throws Exception {
        CommandLineRunner runner = testRunner(true);
        nonEmptyDatabase();
        existingRecordsExcept();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(new User("admin", "h", "a@b.c", null)));

        runner.run();

        verify(userRepository, times(1)).save(any(User.class));
        verify(userRepository).save(argThat(user -> "testuser".equals(user.getUsername())));
        verify(openSloRepository, never()).save(any());
        verifyNothingDeleted();
    }

    @Test
    void databaseCheckFailureSkipsSeeding() throws Exception {
        CommandLineRunner runner = testRunner(false);
        when(openSloRepository.count()).thenThrow(new RuntimeException("table missing"));

        runner.run();

        verifyNothingSaved();
        verifyNothingDeleted();
    }

    @Test
    void liveModeIgnoresSeedFlag() throws Exception {
        ReflectionTestUtils.setField(dataInitializer, "seedRequested", true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        CommandLineRunner runner = liveRunner();

        runner.run();

        verify(userRepository, times(1)).save(argThat(user -> "admin".equals(user.getUsername())));
        verifyNoDemoDataTouched();
    }
}
