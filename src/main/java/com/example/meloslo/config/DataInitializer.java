package com.example.meloslo.config;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    
    @Value("${app.datasource.mode:test}")
    private String datasourceMode;

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(OpenSloRepository repository, MetricRepository metricRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("Starting startup database verification...");
            boolean needsInitialization = false;

            try {
                // 0. Check users
                if (userRepository.findByUsername("admin").isEmpty() || userRepository.findByUsername("testuser").isEmpty()) {
                    log.warn("Required users not found.");
                    needsInitialization = true;
                }

                if (!needsInitialization) {
                    // 1. Check if required records exist
                    List<String> requiredNames = List.of(
                        "payment-service", "engineering-core", "prometheus-prod", "latency-sli", 
                        "availability-sli", "error-sli", "eng-latency-sli", "multi-source-sli", 
                        "web-latency", "api-availability", "db-errors", "eng-latency-slo",
                        "slack-notifications", "email-alerts", "pagerduty-alerts", "discord-alerts"
                    );
                    
                    for (String name : requiredNames) {
                        if (repository.findByName(name).isEmpty()) {
                            log.warn("Required test record '{}' not found.", name);
                            needsInitialization = true;
                            break;
                        }
                    }
                }

                if (!needsInitialization) {
                    // 2. Check if all SLIs have 30 days of metrics
                    List<OpenSlo> allSlis = repository.findByKind("SLI");
                    for (OpenSlo sli : allSlis) {
                        long count = metricRepository.countBySli(sli);
                        if (count < 30) {
                            log.warn("SLI '{}' has only {} metrics (expected 30).", sli.getName(), count);
                            needsInitialization = true;
                            break;
                        }
                    }
                }

                if (!needsInitialization) {
                    // 3. Check if SLOs are correctly connected to Service and SLIs
                    List<OpenSlo> allSlos = repository.findByKindWithSlis("SLO");
                    for (OpenSlo slo : allSlos) {
                        if (slo.getService() == null || slo.getSlis() == null || slo.getSlis().isEmpty()) {
                            log.warn("SLO '{}' is not correctly connected to Service or SLIs.", slo.getName());
                            needsInitialization = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Database check failed due to error: {}. Tables might be missing or corrupted.", e.getMessage());
                needsInitialization = true;
            }

            if (needsInitialization) {
                if ("live".equalsIgnoreCase(datasourceMode)) {
                    log.info("Database state invalid or incomplete, but datasource mode is LIVE. Seeding basic system users only.");
                    seedSystemUsers(userRepository, passwordEncoder);
                } else {
                    log.info("Database state invalid or incomplete. Reloading test data from scratch...");
                    reloadDatabase(repository, metricRepository, userRepository, passwordEncoder);
                }
            } else {
                log.info("Database verification successful. All test data is correct.");
            }
        };
    }

    private void reloadDatabase(OpenSloRepository repository, MetricRepository metricRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        // Clear existing data in correct order
        metricRepository.deleteAll();
        
        // Clear many-to-many and self-referential associations before deleting
        List<OpenSlo> allRecords = repository.findByKindWithSlis("SLO");
        for (OpenSlo record : allRecords) {
            record.getSlis().clear();
            record.setService(null);
            record.setDatasource(null);
            record.setAlertingSource(null);
        }
        repository.saveAll(allRecords);
        repository.flush();
        
        repository.deleteAll();
        userRepository.deleteAll();

        // 0. Create Users
        userRepository.save(new User("admin", passwordEncoder.encode("admin"), "admin@meloslo.com", null));
        userRepository.save(new User("testuser", passwordEncoder.encode("testuser"), "testuser@finance.com", "Finance,Engineering"));

        // 1. Create a Business Service
        OpenSlo paymentService = new OpenSlo("openslo/v1", "BusinessService", "payment-service", "Payment Service", 
            "apiVersion: openslo/v1\nkind: BusinessService\nmetadata:\n  name: payment-service\nspec:\n  description: Core payment processing service");
        paymentService.setDepartment("Finance");
        paymentService.setManager("Jane Doe");
        paymentService = repository.save(paymentService);

        // 2. Create Engineering Business Service and SLOs
        OpenSlo engineeringService = new OpenSlo("openslo/v1", "BusinessService", "engineering-core", "Engineering Core", 
            "apiVersion: openslo/v1\nkind: BusinessService\nmetadata:\n  name: engineering-core\nspec:\n  description: Core engineering platform services");
        engineeringService.setDepartment("Engineering");
        engineeringService.setManager("Bob Builder");
        engineeringService = repository.save(engineeringService);

        // 3. Create a Data Source
        OpenSlo prometheusSource = new OpenSlo("openslo/v1", "DataSource", "prometheus-prod", "Production Prometheus", 
            "apiVersion: openslo/v1\nkind: DataSource\nmetadata:\n  name: prometheus-prod\nspec:\n  type: prometheus\n  config:\n    url: http://prometheus:9090");
        prometheusSource.setDepartment("Infrastructure");
        prometheusSource.setManager("John Smith");
        prometheusSource.setRefreshRate(15); // Refresh every 15 minutes for testing
        prometheusSource = repository.save(prometheusSource);

        // 4. Create SLIs
        OpenSlo latencySli = createSli(repository, "latency-sli", "Standard Latency SLI", "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])) by (le))", prometheusSource, "Finance");
        OpenSlo availabilitySli = createSli(repository, "availability-sli", "API Availability SLI", "sum(rate(http_requests_total{status=~\"2..\"}[5m])) / sum(rate(http_requests_total[5m]))", prometheusSource, "Finance");
        OpenSlo errorSli = createSli(repository, "error-sli", "Database Error SLI", "rate(db_errors_total[5m])", prometheusSource, "Infrastructure");
        OpenSlo engineeringLatencySli = createSli(repository, "eng-latency-sli", "Engineering Latency SLI", "rate(eng_requests_duration_sum[5m]) / rate(eng_requests_duration_count[5m])", prometheusSource, "Engineering");
        
        OpenSlo multiSourceSli = new OpenSlo("openslo/v1", "SLI", "multi-source-sli", "Multi-Source SLI", 
            "apiVersion: openslo/v1\nkind: SLI\nmetadata:\n  name: multi-source-sli\nspec:\n  metricSources:\n    - type: prometheus\n      spec:\n        query: sum(rate(http_requests_total{job=\"api\"}[5m]))\n    - type: datadog\n      spec:\n        query: avg:system.cpu.idle{host:api-server}");
        multiSourceSli.setDepartment("Infrastructure");
        multiSourceSli.setManager("John Smith");
        multiSourceSli.setDatasource(prometheusSource);
        multiSourceSli = repository.save(multiSourceSli);

        // 4.5 Create Alerting Sources
        OpenSlo slackAlert = new OpenSlo("openslo/v1", "AlertingSource", "slack-notifications", "Slack Notifications", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: slack-notifications\nspec:\n  type: slack-webhook");
        slackAlert.setDepartment("Engineering");
        slackAlert.setManager("Bob Builder");
        slackAlert.setAlertUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        slackAlert.setAlertPayload("{\"text\": \"SLO *${SLO_NAME}* is *${SLO_STATUS}* with value *${SLO_VALUE}%*\"}");
        slackAlert = repository.save(slackAlert);

        OpenSlo emailAlert = new OpenSlo("openslo/v1", "AlertingSource", "email-alerts", "Email Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: email-alerts\nspec:\n  type: email");
        emailAlert.setDepartment("Finance");
        emailAlert.setManager("Jane Doe");
        emailAlert.setAlertUrl("mailto:ops-team@example.com");
        emailAlert.setAlertPayload("Subject: SLO Breach - ${SLO_NAME}\n\nStatus: ${SLO_STATUS}\nCurrent Value: ${SLO_VALUE}%");
        emailAlert = repository.save(emailAlert);

        OpenSlo pagerDutyAlert = new OpenSlo("openslo/v1", "AlertingSource", "pagerduty-alerts", "PagerDuty Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: pagerduty-alerts\nspec:\n  type: pagerduty");
        pagerDutyAlert.setDepartment("Infrastructure");
        pagerDutyAlert.setManager("John Smith");
        pagerDutyAlert.setAlertUrl("https://events.pagerduty.com/v2/enqueue");
        pagerDutyAlert.setAlertPayload("{\"event_action\": \"trigger\", \"payload\": {\"summary\": \"SLO ${SLO_NAME} is ${SLO_STATUS}\", \"severity\": \"critical\"}}");
        pagerDutyAlert = repository.save(pagerDutyAlert);

        OpenSlo discordAlert = new OpenSlo("openslo/v1", "AlertingSource", "discord-alerts", "Discord Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: discord-alerts\nspec:\n  type: discord-webhook");
        discordAlert.setDepartment("Engineering");
        discordAlert.setManager("Bob Builder");
        discordAlert.setAlertUrl("https://discord.com/api/webhooks/000000000000000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        discordAlert.setAlertPayload("{\"content\": \"Alert: SLO **${SLO_NAME}** is **${SLO_STATUS}** with value **${SLO_VALUE}%**\"}");
        discordAlert = repository.save(discordAlert);

        // 5. Create SLOs
        createSlo(repository, "web-latency", "Web App Latency", paymentService, List.of(latencySli), 0.99, emailAlert);
        createSlo(repository, "api-availability", "API Availability", paymentService, List.of(availabilitySli), 0.999, slackAlert);
        createSlo(repository, "db-errors", "Database Error Rate", paymentService, List.of(errorSli), 0.99, pagerDutyAlert);
        createSlo(repository, "eng-latency-slo", "Engineering Core Latency", engineeringService, List.of(engineeringLatencySli), 0.95, discordAlert);

        // 6. Seed metrics for 30 days for each SLI
        log.info("Seeding 30 days of metrics for all SLIs...");
        seedMetrics(metricRepository, latencySli, prometheusSource);
        seedMetrics(metricRepository, availabilitySli, prometheusSource);
        seedMetrics(metricRepository, errorSli, prometheusSource);
        seedMetrics(metricRepository, multiSourceSli, prometheusSource);
        seedMetrics(metricRepository, engineeringLatencySli, prometheusSource);
        
        log.info("Initialization complete.");
    }

    private void seedSystemUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin"), "admin@meloslo.com", null));
        }
        if (userRepository.findByUsername("testuser").isEmpty()) {
            userRepository.save(new User("testuser", passwordEncoder.encode("testuser"), "testuser@finance.com", "Finance,Engineering"));
        }
    }

    private OpenSlo createSli(OpenSloRepository repository, String name, String displayName, String query, OpenSlo datasource, String department) {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", name, displayName, 
            "apiVersion: openslo/v1\nkind: SLI\nmetadata:\n  name: " + name + "\nspec:\n  metricSource:\n    type: prometheus\n    spec:\n      query: " + query);
        sli.setDepartment(department);
        sli.setManager("John Smith");
        sli.setDatasource(datasource);
        return repository.save(sli);
    }

    private void createSlo(OpenSloRepository repository, String name, String displayName, OpenSlo service, List<OpenSlo> slis, double target, OpenSlo alertingSource) {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", name, displayName, 
            "apiVersion: openslo/v1\nkind: SLO\nmetadata:\n  name: " + name + "\nspec:\n  service: " + service.getName() + "\n  indicator: " + slis.get(0).getName() + "\n  timeWindow:\n    - duration: 30d\n  objectives:\n    - target: " + target);
        slo.setService(service);
        slo.setSlis(slis);
        slo.setAlertingSource(alertingSource);
        slo.setDepartment(service.getDepartment());
        slo.setManager(service.getManager());
        repository.save(slo);
    }

    private void seedMetrics(MetricRepository metricRepository, OpenSlo sli, OpenSlo datasource) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            LocalDateTime ts = now.minusDays(i);
            double val = 0.95 + (Math.random() * 0.05);
            metricRepository.save(new SliMetric(ts, val, sli, datasource));
        }
    }
}
