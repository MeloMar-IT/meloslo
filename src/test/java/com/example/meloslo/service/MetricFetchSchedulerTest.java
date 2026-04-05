package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricFetchSchedulerTest {

    @Mock
    private OpenSloRepository openSloRepository;

    @Mock
    private MetricRepository metricRepository;

    @Mock
    private OpenSloService openSloService;

    @Mock
    private AlertingService alertingService;

    @Mock
    private TaskManagementService taskManagementService;

    private MetricFetchScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        scheduler = new MetricFetchScheduler(openSloRepository, metricRepository, openSloService, alertingService, taskManagementService);
    }

    @Test
    void shouldFetchMetricsWhenRefreshIsDue() {
        // Arrange
        OpenSlo dataSource = new OpenSlo();
        dataSource.setId(10L);
        dataSource.setKind("DataSource");
        dataSource.setName("test-ds");
        dataSource.setRefreshRate(15);
        dataSource.setLastRefreshTime(LocalDateTime.now().minusMinutes(20));

        OpenSlo sli = new OpenSlo();
        sli.setKind("SLI");
        sli.setName("test-sli");
        
        dataSource.setIndicatorSlis(List.of(sli));

        when(openSloRepository.findByKind("DataSource")).thenReturn(List.of(dataSource));

        // Act
        scheduler.fetchMetrics();

        // Assert
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Fetch-10"), runnableCaptor.capture());
        
        runnableCaptor.getValue().run();

        verify(metricRepository, atLeastOnce()).save(any(SliMetric.class));
        verify(openSloRepository).save(dataSource);
        assertNotNull(dataSource.getLastRefreshTime());
    }

    @Test
    void shouldNotFetchMetricsWhenRefreshIsNotDue() {
        // Arrange
        OpenSlo dataSource = new OpenSlo();
        dataSource.setKind("DataSource");
        dataSource.setName("test-ds");
        dataSource.setRefreshRate(15);
        dataSource.setLastRefreshTime(LocalDateTime.now().minusMinutes(5));

        when(openSloRepository.findByKind("DataSource")).thenReturn(List.of(dataSource));

        // Act
        scheduler.fetchMetrics();

        // Assert
        verify(metricRepository, never()).save(any(SliMetric.class));
        verify(openSloRepository, never()).save(dataSource);
    }
    
    @Test
    void shouldCheckBreachesForAllSlosEveryTime() {
        // Arrange
        OpenSlo slo = new OpenSlo();
        slo.setKind("SLO");
        slo.setName("test-slo");
        
        when(openSloRepository.findByKind("SLO")).thenReturn(List.of(slo));
        when(openSloRepository.findByKind("DataSource")).thenReturn(Collections.emptyList());

        // Act
        scheduler.fetchMetrics();

        // Assert
        verify(openSloService).populateTransientFields(slo);
        verify(alertingService).sendAlertIfNeeded(slo);
    }

    @Test
    void shouldFetchMetricsIfNeverRefreshed() {
        // Arrange
        OpenSlo dataSource = new OpenSlo();
        dataSource.setId(11L);
        dataSource.setKind("DataSource");
        dataSource.setName("test-ds");
        dataSource.setRefreshRate(15);
        dataSource.setLastRefreshTime(null);

        OpenSlo sli = new OpenSlo();
        sli.setKind("SLI");
        sli.setName("test-sli");
        dataSource.setIndicatorSlis(List.of(sli));

        when(openSloRepository.findByKind("DataSource")).thenReturn(List.of(dataSource));

        // Act
        scheduler.fetchMetrics();

        // Assert
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Fetch-11"), runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(metricRepository, atLeastOnce()).save(any(SliMetric.class));
        verify(openSloRepository).save(dataSource);
    }
}
