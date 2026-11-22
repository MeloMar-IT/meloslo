package com.example.meloslo.config;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.Role;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    @Value("${app.seed:false}")
    private boolean seedRequested;

    @Value("${app.admin.initial-password:}")
    private String initialAdminPassword;

    static final List<String> SEED_RECORD_NAMES = List.of(
        "payment-service", "engineering-core", "prometheus-prod", "latency-sli",
        "availability-sli", "error-sli", "eng-latency-sli", "multi-source-sli",
        "web-latency", "api-availability", "db-errors", "eng-latency-slo",
        "slack-notifications", "email-alerts", "pagerduty-alerts", "discord-alerts"
    );

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(OpenSloRepository repository, MetricRepository metricRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            migrateLegacyMetricScale(metricRepository);
            log.info("Starting startup database verification...");
            boolean live = "live".equalsIgnoreCase(datasourceMode);
            try {
                backfillRoles(userRepository, live);
            } catch (RuntimeException e) {
                log.error("Role backfill failed: {}", e.getMessage());
            }
            if (live) {
                if (seedRequested) {
                    log.warn("app.seed=true is ignored in live mode.");
                }
                ensureLiveAdmin(userRepository, passwordEncoder);
                return;
            }
            boolean empty;
            try {
                empty = isDatabaseEmpty(repository, userRepository);
            } catch (RuntimeException e) {
                log.error("Database check failed: {}. Skipping demo seeding; no data was changed.", e.getMessage());
                return;
            }
            if (empty || seedRequested) {
                log.info(empty ? "Empty database: seeding demo data."
                    : "app.seed=true: adding missing demo records; existing data is kept.");
                seedDemoData(repository, metricRepository, userRepository, passwordEncoder);
                log.warn("TEST mode: demo accounts admin/admin and testuser/testuser are active. Do not expose this instance.");
            } else {
                log.info("Database contains data; demo seeding skipped (set app.seed=true to add missing demo records).");
            }
        };
    }

    boolean isDatabaseEmpty(OpenSloRepository repository, UserRepository userRepository) {
        return repository.count() == 0 && userRepository.count() == 0;
    }

    /**
     * One-time, idempotent migration of metric rows stored on the old fraction scale (0 to 1) to 0 to 100.
     * Rows without a scale marker and a value of at most 1 are multiplied by 100 and marked; the remaining
     * unmarked rows are assumed to be on 0 to 100 already and are only marked. Conversion marks the rows it
     * changes in the same statement, so a retry after a partial failure never converts twice.
     * Errors are deliberately not caught: a failed migration stops startup instead of leaving mixed scales.
     */
    private void migrateLegacyMetricScale(MetricRepository metricRepository) {
        int converted = metricRepository.convertLegacyFractionValues();
        int marked = metricRepository.markLegacyValuesAsPercent();
        if (converted > 0 || marked > 0) {
            log.info("Metric scale migration: converted {} fraction values to percent, marked {} other legacy values.", converted, marked);
        }
    }

    /**
     * Returns the record with the candidate's name, saving the candidate if none exists.
     * Returns null when a record with that name exists but has a different kind: it is kept
     * unchanged and the caller must skip the demo records that depend on it.
     */
    private OpenSlo findOrSave(OpenSloRepository repository, OpenSlo candidate, List<OpenSlo> created) {
        Optional<OpenSlo> existing = repository.findByName(candidate.getName());
        if (existing.isPresent()) {
            OpenSlo found = existing.get();
            if (!candidate.getKind().equals(found.getKind())) {
                log.warn("Existing record '{}' has kind '{}' but demo data expects '{}'. Keeping it unchanged; dependent demo records are skipped.",
                    candidate.getName(), found.getKind(), candidate.getKind());
                return null;
            }
            return found;
        }
        OpenSlo saved = repository.save(candidate);
        created.add(saved);
        return saved;
    }

    private void ensureUser(UserRepository userRepository, PasswordEncoder passwordEncoder, String username, String rawPassword, String email, String departments, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new User(username, passwordEncoder.encode(rawPassword), email, departments, role));
        }
    }

    void seedDemoData(OpenSloRepository repository, MetricRepository metricRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        List<OpenSlo> created = new ArrayList<>();

        ensureUser(userRepository, passwordEncoder, "admin", "admin", "admin@meloslo.com", null, Role.ADMIN);
        ensureUser(userRepository, passwordEncoder, "testuser", "testuser", "testuser@finance.com", "Finance,Engineering", Role.USER);

        // 1. Create a Business Service
        OpenSlo paymentService = new OpenSlo("openslo/v1", "BusinessService", "payment-service", "Payment Service", 
            "apiVersion: openslo/v1\nkind: BusinessService\nmetadata:\n  name: payment-service\nspec:\n  description: Core payment processing service");
        paymentService.setDepartment("Finance");
        paymentService.setManager("Jane Doe");
        paymentService = findOrSave(repository, paymentService, created);

        // 2. Create Engineering Business Service and SLOs
        OpenSlo engineeringService = new OpenSlo("openslo/v1", "BusinessService", "engineering-core", "Engineering Core", 
            "apiVersion: openslo/v1\nkind: BusinessService\nmetadata:\n  name: engineering-core\nspec:\n  description: Core engineering platform services");
        engineeringService.setDepartment("Engineering");
        engineeringService.setManager("Bob Builder");
        engineeringService = findOrSave(repository, engineeringService, created);

        // 3. Create a Data Source
        OpenSlo prometheusSource = new OpenSlo("openslo/v1", "DataSource", "prometheus-prod", "Production Prometheus", 
            "apiVersion: openslo/v1\nkind: DataSource\nmetadata:\n  name: prometheus-prod\nspec:\n  type: prometheus\n  config:\n    url: http://prometheus:9090");
        prometheusSource.setDepartment("Infrastructure");
        prometheusSource.setManager("John Smith");
        prometheusSource.setRefreshRate(15); // Refresh every 15 minutes for testing
        prometheusSource = findOrSave(repository, prometheusSource, created);

        // 4. Create SLIs
        OpenSlo latencySli = createSli(repository, created, "latency-sli", "Standard Latency SLI", "histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])) by (le))", prometheusSource, "Finance");
        OpenSlo availabilitySli = createSli(repository, created, "availability-sli", "API Availability SLI", "sum(rate(http_requests_total{status=~\"2..\"}[5m])) / sum(rate(http_requests_total[5m]))", prometheusSource, "Finance");
        OpenSlo errorSli = createSli(repository, created, "error-sli", "Database Error SLI", "rate(db_errors_total[5m])", prometheusSource, "Infrastructure");
        OpenSlo engineeringLatencySli = createSli(repository, created, "eng-latency-sli", "Engineering Latency SLI", "rate(eng_requests_duration_sum[5m]) / rate(eng_requests_duration_count[5m])", prometheusSource, "Engineering");

        OpenSlo multiSourceSli = null;
        if (prometheusSource != null) {
            multiSourceSli = new OpenSlo("openslo/v1", "SLI", "multi-source-sli", "Multi-Source SLI",
                "apiVersion: openslo/v1\nkind: SLI\nmetadata:\n  name: multi-source-sli\nspec:\n  metricSources:\n    - type: prometheus\n      spec:\n        query: sum(rate(http_requests_total{job=\"api\"}[5m]))\n    - type: datadog\n      spec:\n        query: avg:system.cpu.idle{host:api-server}");
            multiSourceSli.setDepartment("Infrastructure");
            multiSourceSli.setManager("John Smith");
            multiSourceSli.setDatasource(prometheusSource);
            multiSourceSli = findOrSave(repository, multiSourceSli, created);
        }

        // 4.5 Create Alerting Sources
        OpenSlo slackAlert = new OpenSlo("openslo/v1", "AlertingSource", "slack-notifications", "Slack Notifications", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: slack-notifications\nspec:\n  type: slack-webhook");
        slackAlert.setDepartment("Engineering");
        slackAlert.setManager("Bob Builder");
        slackAlert.setAlertUrl("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        slackAlert.setAlertPayload("{\"text\": \"SLO *${SLO_NAME}* is *${SLO_STATUS}* with value *${SLO_VALUE}%*\"}");
        slackAlert = findOrSave(repository, slackAlert, created);

        OpenSlo emailAlert = new OpenSlo("openslo/v1", "AlertingSource", "email-alerts", "Email Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: email-alerts\nspec:\n  type: email");
        emailAlert.setDepartment("Finance");
        emailAlert.setManager("Jane Doe");
        emailAlert.setAlertUrl("mailto:ops-team@example.com");
        emailAlert.setAlertPayload("Subject: SLO Breach - ${SLO_NAME}\n\nStatus: ${SLO_STATUS}\nCurrent Value: ${SLO_VALUE}%");
        emailAlert = findOrSave(repository, emailAlert, created);

        OpenSlo pagerDutyAlert = new OpenSlo("openslo/v1", "AlertingSource", "pagerduty-alerts", "PagerDuty Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: pagerduty-alerts\nspec:\n  type: pagerduty");
        pagerDutyAlert.setDepartment("Infrastructure");
        pagerDutyAlert.setManager("John Smith");
        pagerDutyAlert.setAlertUrl("https://events.pagerduty.com/v2/enqueue");
        pagerDutyAlert.setAlertPayload("{\"event_action\": \"trigger\", \"payload\": {\"summary\": \"SLO ${SLO_NAME} is ${SLO_STATUS}\", \"severity\": \"critical\"}}");
        pagerDutyAlert = findOrSave(repository, pagerDutyAlert, created);

        OpenSlo discordAlert = new OpenSlo("openslo/v1", "AlertingSource", "discord-alerts", "Discord Alerts", 
            "apiVersion: openslo/v1\nkind: AlertingSource\nmetadata:\n  name: discord-alerts\nspec:\n  type: discord-webhook");
        discordAlert.setDepartment("Engineering");
        discordAlert.setManager("Bob Builder");
        discordAlert.setAlertUrl("https://discord.com/api/webhooks/000000000000000000/XXXXXXXXXXXXXXXXXXXXXXXX");
        discordAlert.setAlertPayload("{\"content\": \"Alert: SLO **${SLO_NAME}** is **${SLO_STATUS}** with value **${SLO_VALUE}%**\"}");
        discordAlert = findOrSave(repository, discordAlert, created);

        // 5. Create SLOs
        createSlo(repository, created, "web-latency", "Web App Latency", paymentService, latencySli, 0.99, emailAlert);
        createSlo(repository, created, "api-availability", "API Availability", paymentService, availabilitySli, 0.999, slackAlert);
        createSlo(repository, created, "db-errors", "Database Error Rate", paymentService, errorSli, 0.99, pagerDutyAlert);
        createSlo(repository, created, "eng-latency-slo", "Engineering Core Latency", engineeringService, engineeringLatencySli, 0.95, discordAlert);

        // 6. Seed 30 days of metrics, only for SLIs created in this run
        for (OpenSlo record : created) {
            if ("SLI".equals(record.getKind())) {
                seedMetrics(metricRepository, record, prometheusSource);
            }
        }

        log.info("Initialization complete.");
    }

    void ensureLiveAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        if (userRepository.findByUsername("admin").isPresent()) {
            log.info("LIVE mode: admin exists, no users created.");
            return;
        }
        String password = resolveInitialAdminPassword();
        userRepository.save(new User("admin", passwordEncoder.encode(password), "admin@meloslo.com", null, Role.ADMIN));
    }

    /** Gives every user without a role one: "admin" becomes ADMIN, everyone else USER. */
    private void backfillRoles(UserRepository userRepository, boolean live) {
        for (User user : userRepository.findAll()) {
            if (user.getRole() != null) {
                continue;
            }
            if ("admin".equals(user.getUsername())) {
                user.setRole(Role.ADMIN);
                // In live mode the promoted admin may still have the default password.
                user.setPasswordChangeRequired(live);
                log.warn("Migrated user 'admin' to role ADMIN{}", live ? "; password change required" : "");
            } else {
                user.setRole(Role.USER);
            }
            userRepository.save(user);
        }
    }

    }

    String resolveInitialAdminPassword() {
        if (initialAdminPassword != null && !initialAdminPassword.isBlank()) {
            log.info("Using configured initial admin password");
            return initialAdminPassword;
        }
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("No app.admin.initial-password set. Generated initial password for user 'admin': {} (change it after first login)", password);
        return password;
    }

    /** Returns null when the data source is missing (e.g. a kind conflict), so dependent SLIs are skipped. */
    private OpenSlo createSli(OpenSloRepository repository, List<OpenSlo> created, String name, String displayName, String query, OpenSlo datasource, String department) {
        if (datasource == null) {
            log.warn("Skipping demo SLI '{}': its data source is not available.", name);
            return null;
        }
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", name, displayName,
            "apiVersion: openslo/v1\nkind: SLI\nmetadata:\n  name: " + name + "\nspec:\n  metricSource:\n    type: prometheus\n    spec:\n      query: " + query);
        sli.setDepartment(department);
        sli.setManager("John Smith");
        sli.setDatasource(datasource);
        return findOrSave(repository, sli, created);
    }

    /** Returns null when the service, SLI or alerting source is missing (e.g. a kind conflict), so the SLO is skipped. */
    private OpenSlo createSlo(OpenSloRepository repository, List<OpenSlo> created, String name, String displayName, OpenSlo service, OpenSlo sli, double target, OpenSlo alertingSource) {
        if (service == null || sli == null || alertingSource == null) {
            log.warn("Skipping demo SLO '{}': a service, SLI or alerting source is not available.", name);
            return null;
        }
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", name, displayName,
            "apiVersion: openslo/v1\nkind: SLO\nmetadata:\n  name: " + name + "\nspec:\n  service: " + service.getName() + "\n  indicator: " + sli.getName() + "\n  timeWindow:\n    - duration: 30d\n  objectives:\n    - target: " + target);
        slo.setService(service);
        slo.setSlis(new ArrayList<>(List.of(sli)));
        slo.setAlertingSource(alertingSource);
        slo.setDepartment(service.getDepartment());
        slo.setManager(service.getManager());
        return findOrSave(repository, slo, created);
    }

    private void seedMetrics(MetricRepository metricRepository, OpenSlo sli, OpenSlo datasource) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 30; i++) {
            LocalDateTime ts = now.minusDays(i);
            double val = 95.0 + (Math.random() * 5.0);
            metricRepository.save(new SliMetric(ts, val, sli, datasource));
        }
    }
}
