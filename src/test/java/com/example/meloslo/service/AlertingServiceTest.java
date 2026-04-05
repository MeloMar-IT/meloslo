package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.OpenSloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertingServiceTest {

    private OpenSloRepository openSloRepository;
    private RestTemplate restTemplate;
    private TaskManagementService taskManagementService;
    private AlertingService alertingService;

    @BeforeEach
    void setUp() {
        openSloRepository = mock(OpenSloRepository.class);
        restTemplate = mock(RestTemplate.class);
        taskManagementService = mock(TaskManagementService.class);
        alertingService = new AlertingService(openSloRepository, restTemplate, taskManagementService);
    }

    @Test
    void shouldSendAlertWhenBreaching() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");
        source.setAlertPayload("{\"text\": \"SLO ${SLO_NAME} is ${SLO_STATUS} with value ${SLO_VALUE}%\"}");

        OpenSlo slo = new OpenSlo();
        slo.setId(1L);
        slo.setKind("SLO");
        slo.setName("test-slo");
        slo.setDisplayName("Test SLO");
        slo.setStatus("Breaching");
        slo.setCurrentValue(0.45);
        slo.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-1"), runnableCaptor.capture());
        
        runnableCaptor.getValue().run();

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://mock-webhook"), entityCaptor.capture(), eq(String.class));

        String payload = (String) entityCaptor.getValue().getBody();
        assertTrue(payload.contains("Test SLO"), "Payload should contain SLO display name");
        assertTrue(payload.contains("Breaching"), "Payload should contain status");
        // Values are formatted with %.2f, so 0.45 becomes "0.45"
        assertTrue(payload.contains("0.45"), "Payload should contain current value. Got: " + payload);

        verify(openSloRepository).save(slo);
        assertNotNull(slo.getLastAlertTime());
    }

    @Test
    void shouldNotAlertIfAlreadyAlertedRecently() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");

        OpenSlo slo = new OpenSlo();
        slo.setKind("SLO");
        slo.setStatus("Breaching");
        slo.setAlertingSource(source);
        // Test with 5 hours ago (should still be suppressed)
        slo.setLastAlertTime(LocalDateTime.now().minusHours(5));

        alertingService.sendAlertIfNeeded(slo);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void shouldAlertIfLastAlertWasLongAgo() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");
        source.setAlertPayload("${SLO_NAME}");

        OpenSlo slo = new OpenSlo();
        slo.setId(2L);
        slo.setKind("SLO");
        slo.setName("old-alert-slo");
        slo.setStatus("Breaching");
        slo.setAlertingSource(source);
        // Test with 7 hours ago (should alert)
        slo.setLastAlertTime(LocalDateTime.now().minusHours(7));

        alertingService.sendAlertIfNeeded(slo);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-2"), runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(restTemplate).postForEntity(eq("http://mock-webhook"), any(), eq(String.class));
    }

    @Test
    void shouldNotAlertIfHealthy() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");

        OpenSlo slo = new OpenSlo();
        slo.setKind("SLO");
        slo.setStatus("Healthy");
        slo.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }
}
