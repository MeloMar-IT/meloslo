package com.example.meloslo.config;

import com.example.meloslo.repository.OpenSloRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Starts the real application with the migration switched on and runs it through its entry point. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:migrationrunnertest",
        "app.encryption.reencrypt-on-startup=true"})
class EncryptionMigrationRunnerTest {

    @Autowired
    private EncryptionMigration migration;

    @Autowired
    private OpenSloRepository repository;

    @Test
    void runRewritesEveryRowWithoutAnOuterTransaction() {
        long before = repository.count();
        assertThat(before).isGreaterThan(0);

        migration.run(new DefaultApplicationArguments());

        assertThat(repository.count()).isEqualTo(before);
        assertThat(migration.reencryptAll()).isEqualTo((int) before);
        repository.findAll().forEach(r -> assertThat(r.getName()).isNotBlank());
    }
}
