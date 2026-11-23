package com.example.meloslo.config;

import com.example.meloslo.MeloSloApplication;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.User;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import com.example.meloslo.util.EncryptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restarts the real application against a file based H2 database and checks
 * that the startup seeding never deletes or overwrites existing data.
 */
class DataInitializerRestartTest {

    private static final int SEED_RECORDS = DataInitializer.SEED_RECORD_NAMES.size();

    @TempDir
    Path dir;

    @AfterEach
    void restoreDefaultKey() {
        // The key state is static and shared by the whole JVM
        EncryptionUtils.configure("", "", false);
    }

    private ConfigurableApplicationContext start(String... extraArgs) {
        String url = "jdbc:h2:file:" + dir.resolve("db").toAbsolutePath();
        List<String> args = new ArrayList<>(List.of(
            "--spring.datasource.url=" + url,
            "--server.port=0",
            "--app.datasource.mode=test",
            // A persistent datasource requires a key; the same one on every restart
            "--app.encryption.key=restart-test-key"));
        args.addAll(List.of(extraArgs));
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(MeloSloApplication.class)
            // Keep the real initializer and database, but do not run the metric fetch scheduler.
            .initializers(c -> c.addBeanFactoryPostProcessor(bf ->
                ((BeanDefinitionRegistry) bf).removeBeanDefinition("metricFetchScheduler")))
            .run(args.toArray(new String[0]));
        assertThat(ctx.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(url);
        return ctx;
    }

    private static boolean seedFlag(ConfigurableApplicationContext ctx) {
        return ctx.getEnvironment().getProperty("app.seed", Boolean.class, false);
    }

    /** Identity and content of every record, user and metric, including relationships. */
    private static Map<String, String> snapshot(ConfigurableApplicationContext ctx) {
        return ctx.getBean(TransactionTemplate.class).execute(status -> {
            Map<String, String> snap = new TreeMap<>();
            for (OpenSlo r : ctx.getBean(OpenSloRepository.class).findAll()) {
                String slis = r.getSlis().stream().map(OpenSlo::getName).sorted().collect(Collectors.joining(","));
                snap.put("record:" + r.getName(), String.join("|", String.valueOf(r.getId()), r.getKind(),
                    String.valueOf(r.getSpec()), String.valueOf(r.getDepartment()), String.valueOf(r.getManager()),
                    r.getService() == null ? "-" : r.getService().getName(),
                    r.getDatasource() == null ? "-" : r.getDatasource().getName(),
                    r.getAlertingSource() == null ? "-" : r.getAlertingSource().getName(), slis));
            }
            for (User u : ctx.getBean(UserRepository.class).findAll()) {
                snap.put("user:" + u.getUsername(), String.join("|", String.valueOf(u.getId()),
                    u.getPassword(), u.getEmail(), String.valueOf(u.getDepartments())));
            }
            for (SliMetric m : ctx.getBean(MetricRepository.class).findAll()) {
                snap.put(String.format("metric:%08d", m.getId()),
                    m.getSli().getName() + "|" + m.getValue() + "|" + m.getTimestamp());
            }
            return snap;
        });
    }

    private static long count(Map<String, String> snap, String prefix) {
        return snap.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    private static void addUserData(ConfigurableApplicationContext ctx) {
        OpenSloRepository records = ctx.getBean(OpenSloRepository.class);
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "user-sli", "User SLI", "kind: SLI");
        sli.setDepartment("Finance");
        sli.setManager("Someone");
        sli = records.save(sli);
        MetricRepository metrics = ctx.getBean(MetricRepository.class);
        metrics.save(new SliMetric(LocalDateTime.now(), 0.91, sli, null));
        metrics.save(new SliMetric(LocalDateTime.now().minusDays(1), 0.92, sli, null));
        ctx.getBean(UserRepository.class).save(new User("alice", "alice-hash", "alice@example.com", "Finance"));
    }

    private static void deleteDiscordAlerts(ConfigurableApplicationContext ctx) {
        ctx.getBean(TransactionTemplate.class).executeWithoutResult(status -> {
            OpenSloRepository records = ctx.getBean(OpenSloRepository.class);
            OpenSlo slo = records.findByName("eng-latency-slo").orElseThrow();
            slo.setAlertingSource(null);
            records.saveAndFlush(slo);
            records.delete(records.findByName("discord-alerts").orElseThrow());
        });
    }

    @Test
    void restartWithUserCreatedSliKeepsAllData() {
        Map<String, String> before;
        try (ConfigurableApplicationContext ctx = start()) {
            assertThat(seedFlag(ctx)).isFalse();
            Map<String, String> seeded = snapshot(ctx);
            assertThat(count(seeded, "record:")).isEqualTo(SEED_RECORDS);
            assertThat(seeded).containsKeys("user:admin", "user:testuser");
            assertThat(count(seeded, "metric:")).isEqualTo(150);
            addUserData(ctx);
            before = snapshot(ctx);
        }
        assertThat(before).containsKeys("record:user-sli", "user:alice");
        assertThat(count(before, "metric:")).isEqualTo(152);

        try (ConfigurableApplicationContext ctx = start()) {
            assertThat(snapshot(ctx)).isEqualTo(before);
        }
    }

    @Test
    void restartAfterDeletingSeedRecordDoesNotRecreateOrWipe() {
        Map<String, String> before;
        try (ConfigurableApplicationContext ctx = start()) {
            addUserData(ctx);
            deleteDiscordAlerts(ctx);
            before = snapshot(ctx);
        }
        assertThat(before).doesNotContainKey("record:discord-alerts");

        try (ConfigurableApplicationContext ctx = start()) {
            assertThat(snapshot(ctx)).isEqualTo(before);
        }
    }

    @Test
    void restartWithSeedFlagRestoresOnlyMissingRecords() {
        Map<String, String> before;
        try (ConfigurableApplicationContext ctx = start()) {
            addUserData(ctx);
            deleteDiscordAlerts(ctx);
            before = snapshot(ctx);
        }

        Map<String, String> after;
        try (ConfigurableApplicationContext ctx = start("--app.seed=true")) {
            assertThat(seedFlag(ctx)).isTrue();
            after = snapshot(ctx);
        }

        assertThat(after).containsKey("record:discord-alerts");
        Map<String, String> withoutRestored = new TreeMap<>(after);
        withoutRestored.remove("record:discord-alerts");
        // Everything else is untouched: same ids, passwords, links and exactly the same metrics.
        assertThat(withoutRestored).isEqualTo(before);
        assertThat(after.get("record:eng-latency-slo")).isEqualTo(before.get("record:eng-latency-slo"));
        assertThat(after.get("user:alice")).isEqualTo(before.get("user:alice"));
    }
}
