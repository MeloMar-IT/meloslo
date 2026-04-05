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
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);
    private final RestTemplate restTemplate;
    private final OpenSloRepository openSloRepository;
    private final TaskManagementService taskManagementService;

    @Autowired
    public AlertingService(OpenSloRepository openSloRepository, RestTemplate restTemplate, TaskManagementService taskManagementService) {
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

            String payload = replaceVariables(payloadTemplate, currentSlo);

            try {
                log.info("Sending alert for SLO {} to {}", currentSlo.getName(), url);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(payload, headers);

                restTemplate.postForEntity(url, entity, String.class);

                currentSlo.setLastAlertTime(LocalDateTime.now());
                openSloRepository.save(currentSlo);
            } catch (Exception e) {
                log.error("Failed to send alert for SLO {}: {}", currentSlo.getName(), e.getMessage());
            }
        });
    }

    private String replaceVariables(String template, OpenSlo slo) {
        if (template == null) return "";
        
        String result = template;
        result = result.replace("${SLO_NAME}", slo.getDisplayName() != null ? slo.getDisplayName() : slo.getName());
        result = result.replace("${SLO_STATUS}", slo.getStatus() != null ? slo.getStatus() : "Unknown");
        result = result.replace("${SLO_VALUE}", String.format(Locale.US, "%.2f", slo.getCurrentValue() != null ? slo.getCurrentValue() : 0.0));
        
        return result;
    }
}
