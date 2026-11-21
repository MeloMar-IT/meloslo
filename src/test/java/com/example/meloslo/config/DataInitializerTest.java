package com.example.meloslo.config;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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

    @Test
    void shouldSeedOnlySystemUsersInLiveModeWhenNeeded() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(dataInitializer, "datasourceMode", "live");
        
        // Mock that admin user is missing to trigger initialization
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        CommandLineRunner runner = dataInitializer.initDatabase(openSloRepository, metricRepository, userRepository, passwordEncoder);

        // Act
        runner.run();

        // Assert
        // In LIVE mode, it should only seed system users
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(userRepository).save(argThat(user -> "admin".equals(user.getUsername())));
        verify(userRepository).save(argThat(user -> "testuser".equals(user.getUsername())));
        
        // It should NOT call reloadDatabase (which deletes everything and adds test data)
        verify(openSloRepository, never()).deleteAll();
        verify(metricRepository, never()).deleteAll();
        // and should not save OpenSlo records (test data)
        verify(openSloRepository, never()).save(any());
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
