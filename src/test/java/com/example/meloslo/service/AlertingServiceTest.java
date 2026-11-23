package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.OpenSloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class AlertingServiceTest {

    private OpenSloRepository openSloRepository;
    private RestTemplate restTemplate;
    private TaskManagementService taskManagementService;
    private AlertingService alertingService;
    private AlertUrlValidator alertUrlValidator;

    @BeforeEach
    void setUp() {
        openSloRepository = mock(OpenSloRepository.class);
        restTemplate = mock(RestTemplate.class);
        taskManagementService = mock(TaskManagementService.class);
        alertUrlValidator = mock(AlertUrlValidator.class);
        alertingService = new AlertingService(openSloRepository, restTemplate, taskManagementService, alertUrlValidator);
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
        slo.setCurrentValue(99.25);
        slo.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);
        
        when(openSloRepository.findById(1L)).thenReturn(java.util.Optional.of(slo));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-1"), runnableCaptor.capture());
        
        runnableCaptor.getValue().run();

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://mock-webhook"), entityCaptor.capture(), eq(String.class));

        String payload = (String) entityCaptor.getValue().getBody();
        assertTrue(payload.contains("Test SLO"), "Payload should contain SLO display name");
        assertTrue(payload.contains("Breaching"), "Payload should contain status");
        // Values are on the 0 to 100 scale, formatted with 3 decimals like the dashboard
        assertTrue(payload.contains("99.250"), "Payload should contain current value. Got: " + payload);

        verify(openSloRepository).save(slo);
        assertNotNull(slo.getLastAlertTime());
    }

    private Runnable queuedAlertTask(OpenSlo slo) {
        alertingService.sendAlertIfNeeded(slo);
        when(openSloRepository.findById(slo.getId())).thenReturn(java.util.Optional.of(slo));
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-" + slo.getId()), runnableCaptor.capture());
        return runnableCaptor.getValue();
    }

    private OpenSlo breachingSloWithUrl(long id, String url) {
        OpenSlo source = new OpenSlo();
        source.setName("hook-source");
        source.setKind("AlertingSource");
        source.setAlertUrl(url);
        source.setAlertPayload("{}");
        OpenSlo slo = new OpenSlo();
        slo.setId(id);
        slo.setKind("SLO");
        slo.setName("slo-" + id);
        slo.setStatus("Breaching");
        slo.setAlertingSource(source);
        return slo;
    }

    @Test
    void shouldNotPostWhenStoredUrlFailsValidation() {
        OpenSlo slo = breachingSloWithUrl(10L, "http://127.0.0.1/hook");
        doThrow(new BadRequestException("Alert URL points to a private or internal address"))
                .when(alertUrlValidator).validate("http://127.0.0.1/hook");

        queuedAlertTask(slo).run();

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        assertNull(slo.getLastAlertTime());
    }

    @Test
    void failureLogShowsHostAndCategoryButNeverPathOrMessage(CapturedOutput output) {
        String url = "https://hooks.example.test/SECRETPATH";
        OpenSlo slo = breachingSloWithUrl(11L, url);
        when(restTemplate.postForEntity(eq(url), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("I/O error on POST " + url));
        Runnable task = queuedAlertTask(slo);

        task.run();

        String log = output.getAll();
        assertTrue(log.contains("hooks.example.test"), log);
        assertTrue(log.contains("ResourceAccessException"), log);
        assertFalse(log.contains("SECRETPATH"), log);
    }

    @Test
    void httpErrorLogShowsStatusOnly(CapturedOutput output) {
        String url = "https://hooks.example.test/SECRETPATH";
        OpenSlo slo = breachingSloWithUrl(12L, url);
        when(restTemplate.postForEntity(eq(url), any(), eq(String.class))).thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found",
                        new org.springframework.http.HttpHeaders(), "body SECRETPATH".getBytes(), null));
        Runnable task = queuedAlertTask(slo);

        task.run();

        String log = output.getAll();
        assertTrue(log.contains("HTTP 404"), log);
        assertFalse(log.contains("SECRETPATH"), log);
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

        when(openSloRepository.findById(2L)).thenReturn(java.util.Optional.of(slo));

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

    @Test
    void shouldHandleMissingVariablesInPayload() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");
        source.setAlertPayload("Fixed message with no variables");

        OpenSlo slo = new OpenSlo();
        slo.setId(3L);
        slo.setKind("SLO");
        slo.setName("test-slo");
        slo.setStatus("Breaching");
        slo.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);

        when(openSloRepository.findById(3L)).thenReturn(java.util.Optional.of(slo));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-3"), runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(restTemplate).postForEntity(eq("http://mock-webhook"), any(), eq(String.class));
    }

    @Test
    void shouldHandleNullValuesInVariables() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");
        source.setAlertPayload("${SLO_NAME} ${SLO_STATUS} ${SLO_VALUE}");

        OpenSlo slo = new OpenSlo();
        slo.setId(4L);
        slo.setKind("SLO");
        slo.setName("null-vars-slo");
        slo.setStatus("Breaching");
        slo.setDisplayName(null);
        slo.setCurrentValue(null);
        slo.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);

        when(openSloRepository.findById(4L)).thenReturn(java.util.Optional.of(slo));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-4"), runnableCaptor.capture());
        runnableCaptor.getValue().run();

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        
        String payload = (String) entityCaptor.getValue().getBody();
        assertTrue(payload.contains("null-vars-slo"));
        assertTrue(payload.contains("Breaching"));
        assertTrue(payload.contains("0.000"));
    }

    @Test
    void shouldUseSchedulerComputedStatusAndValueWhenRefetchedEntityHasNone() {
        OpenSlo source = new OpenSlo();
        source.setKind("AlertingSource");
        source.setAlertUrl("http://mock-webhook");
        source.setAlertPayload("${SLO_STATUS} ${SLO_VALUE}%");

        OpenSlo slo = new OpenSlo();
        slo.setId(5L);
        slo.setKind("SLO");
        slo.setName("computed-slo");
        slo.setStatus("Breaching");
        slo.setCurrentValue(99.25);
        slo.setAlertingSource(source);

        // The re-fetched entity has no computed (transient) fields
        OpenSlo fresh = new OpenSlo();
        fresh.setId(5L);
        fresh.setKind("SLO");
        fresh.setName("computed-slo");
        fresh.setAlertingSource(source);

        alertingService.sendAlertIfNeeded(slo);
        when(openSloRepository.findById(5L)).thenReturn(java.util.Optional.of(fresh));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManagementService).runAsyncTask(eq("Alert-5"), runnableCaptor.capture());
        runnableCaptor.getValue().run();

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));
        assertEquals("Breaching 99.250%", entityCaptor.getValue().getBody());
    }
}
