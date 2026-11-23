package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.OpenSloRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);
    private final RestTemplate restTemplate;
    private final OpenSloRepository openSloRepository;
    private final TaskManagementService taskManagementService;
    private final AlertUrlValidator alertUrlValidator;

    @Autowired
    public AlertingService(OpenSloRepository openSloRepository, RestTemplate restTemplate, TaskManagementService taskManagementService,
                           AlertUrlValidator alertUrlValidator) {
        this.alertUrlValidator = alertUrlValidator;
        this.openSloRepository = openSloRepository;
        this.restTemplate = restTemplate;
        this.taskManagementService = taskManagementService;
    }

    public void sendAlertIfNeeded(OpenSlo slo) {
        if (slo == null || !"SLO".equalsIgnoreCase(slo.getKind()) || slo.getAlertingSource() == null) {
            return;
        }

        if (!"Breaching".equalsIgnoreCase(slo.getStatus())) {
            return;
        }

        // Prevent spam: only alert once every 6 hours per SLO
        if (slo.getLastAlertTime() != null && slo.getLastAlertTime().plusHours(6).isAfter(LocalDateTime.now())) {
            return;
        }

        String taskId = "Alert-" + slo.getId();
        Long sloId = slo.getId();
        // Status and value are computed fields; the re-fetched entity does not carry them
        final String status = slo.getStatus();
        final Double value = slo.getCurrentValue();
        taskManagementService.runAsyncTask(taskId, () -> {
            OpenSlo currentSlo = openSloRepository.findById(sloId).orElse(null);
            if (currentSlo == null || !"SLO".equalsIgnoreCase(currentSlo.getKind()) || currentSlo.getAlertingSource() == null) {
                return;
            }

            OpenSlo source = currentSlo.getAlertingSource();
            String url = source.getAlertUrl();
            String payloadTemplate = source.getAlertPayload();

            if (url == null || url.isBlank()) {
                log.warn("AlertingSource {} has no URL configured", source.getName());
                return;
            }

            try {
                alertUrlValidator.validate(url);
            } catch (BadRequestException e) {
                log.warn("Alert for SLO {} not sent: alert URL of source {} (host {}) refused: {}",
                        currentSlo.getName(), source.getName(), AlertUrlValidator.hostOf(url), e.getMessage());
                return;
            }

            String payload = replaceVariables(payloadTemplate, currentSlo, status, value);

            try {
                log.info("Sending alert for SLO {} to host {}", currentSlo.getName(), AlertUrlValidator.hostOf(url));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(payload, headers);

                restTemplate.postForEntity(url, entity, String.class);

                currentSlo.setLastAlertTime(LocalDateTime.now());
                openSloRepository.save(currentSlo);
            } catch (Exception e) {
                // Never log e.getMessage(): it can contain the full URL and the response body
                String reason = e instanceof RestClientResponseException rre
                        ? "HTTP " + rre.getStatusCode().value()
                        : e.getClass().getSimpleName();
                log.error("Failed to send alert for SLO {} to host {}: {}", currentSlo.getName(),
                        AlertUrlValidator.hostOf(url), reason);
            }
        });
    }

    private String replaceVariables(String template, OpenSlo slo, String status, Double value) {
        if (template == null) return "";
        
        String result = template;
        result = result.replace("${SLO_NAME}", slo.getDisplayName() != null ? slo.getDisplayName() : slo.getName());
        result = result.replace("${SLO_STATUS}", status != null ? status : "Unknown");
        result = result.replace("${SLO_VALUE}", String.format(Locale.US, "%.3f", value != null ? value : 0.0));
        
        return result;
    }
}
