package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricFetchScheduler.class);

    private final OpenSloRepository openSloRepository;
    private final MetricRepository metricRepository;
    private final OpenSloService openSloService;
    private final AlertingService alertingService;
    private final TaskManagementService taskManagementService;
    private final RestTemplate restTemplate;

    @Value("${app.datasource.mode:test}")
    private String datasourceMode;

    @Autowired
    public MetricFetchScheduler(OpenSloRepository openSloRepository, 
                                MetricRepository metricRepository,
                                OpenSloService openSloService,
                                AlertingService alertingService,
                                TaskManagementService taskManagementService,
                                RestTemplate restTemplate) {
        this.openSloRepository = openSloRepository;
        this.metricRepository = metricRepository;
        this.openSloService = openSloService;
        this.alertingService = alertingService;
        this.taskManagementService = taskManagementService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedRate = 900000) // Run every 15 minutes to check for updates and breaches
    @Transactional
    public void fetchMetrics() {
        List<OpenSlo> dataSources = openSloRepository.findByKind("DataSource");
        LocalDateTime now = LocalDateTime.now();

        log.info("Checking for SLO breaches...");
        List<OpenSlo> allSlos = openSloRepository.findByKind("SLO");
        for (OpenSlo slo : allSlos) {
            openSloService.populateTransientFields(slo);
            alertingService.sendAlertIfNeeded(slo);
        }

        for (OpenSlo ds : dataSources) {
            Integer refreshRate = ds.getRefreshRate();
            if (refreshRate == null) {
                refreshRate = 60; // Default 60 minutes
            }
            if (refreshRate < 15) {
                refreshRate = 15; // Minimum 15 minutes
            }

            LocalDateTime lastRefresh = ds.getLastRefreshTime();
            // Fetch if never refreshed or if refreshRate minutes passed since last refresh
            if (lastRefresh == null || lastRefresh.plusMinutes(refreshRate).isBefore(now)) {
                String taskId = "Fetch-" + ds.getId();
                final Integer finalRefreshRate = refreshRate;
                taskManagementService.runAsyncTask(taskId, () -> {
                    log.info("Fetching metrics for DataSource: {} (Mode: {}, Refresh Rate: {}m)", ds.getName(), datasourceMode, finalRefreshRate);
                    
                    List<OpenSlo> slis = ds.getIndicatorSlis();
                    if (slis.isEmpty()) {
                        log.debug("No SLIs linked to DataSource: {}", ds.getName());
                    }

                    for (OpenSlo sli : slis) {
                        try {
                            double value;
                            if ("live".equalsIgnoreCase(datasourceMode)) {
                                value = liveFetch(ds, sli);
                            } else {
                                value = simulateFetch(ds, sli);
                            }
                            metricRepository.save(new SliMetric(LocalDateTime.now(), value, sli, ds));
                            log.debug("Fetched metric for SLI {}: {}", sli.getName(), value);
                        } catch (Exception e) {
                            log.error("Failed to fetch metric for SLI {}: {}. Setting value to 0.", sli.getName(), e.getMessage());
                            metricRepository.save(new SliMetric(LocalDateTime.now(), 0.0, sli, ds));
                        }
                    }
                    
                    ds.setLastRefreshTime(LocalDateTime.now());
                    openSloRepository.save(ds);
                });
            }
        }
    }

    private double simulateFetch(OpenSlo ds, OpenSlo sli) {
        // Mock failure randomly (3% chance)
        if (Math.random() < 0.03) {
            throw new RuntimeException("Mock connection error for " + ds.getName());
        }
        // Return a realistic health value (0.9 to 1.0)
        return 0.9 + (Math.random() * 0.1);
    }

    private double liveFetch(OpenSlo ds, OpenSlo sli) {
        // Implement real fetching from external source (e.g., Prometheus)
        log.info("Performing LIVE fetch for SLI: {} from DataSource: {}", sli.getName(), ds.getName());
        
        // This is a skeleton/placeholder for real implementation.
        // It uses RestTemplate to connect to ds.getAlertUrl() or similar.
        // Since we don't have a real endpoint, we default to simulateFetch but with extra logging
        // to show we are in 'live' mode.
        try {
            // Real implementation would parse the spec and make a request.
            // Example for Prometheus:
            // String query = openSloService.parseQueryFromSpec(sli.getSpec());
            // String url = ds.getAlertUrl() + "/api/v1/query?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            // JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            // return parsePrometheusResponse(response);
            
            return simulateFetch(ds, sli); 
        } catch (Exception e) {
            log.error("Live fetch failed: {}", e.getMessage());
            throw e;
        }
    }
}
