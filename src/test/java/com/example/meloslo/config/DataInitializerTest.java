package com.example.meloslo.config;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        verifyNoInteractions(openSloRepository, metricRepository);
        verify(userRepository, never()).deleteAll();
        verify(userRepository, never()).save(argThat(user -> "testuser".equals(user.getUsername())));
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

    @Test
    void shouldReloadFullDatabaseInTestModeWhenNeeded() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(dataInitializer, "datasourceMode", "test");

        // Mock that admin user is missing to trigger initialization
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        // Return dummy objects to avoid NPE in reloadDatabase
        OpenSlo dummySlo = new OpenSlo();
        dummySlo.setName("dummy");
        dummySlo.setDepartment("Engineering");
        dummySlo.setManager("Manager");
        when(openSloRepository.save(any(OpenSlo.class))).thenReturn(dummySlo);

        CommandLineRunner runner = dataInitializer.initDatabase(openSloRepository, metricRepository, userRepository, passwordEncoder);

        // Act
        runner.run();

        // Assert
        // In TEST mode, it should reload the database
        verify(userRepository).deleteAll();
        verify(openSloRepository).deleteAll();
        verify(metricRepository).deleteAll();

        // It should save OpenSlo records (test data)
        verify(openSloRepository, atLeastOnce()).save(any());
    }
}
