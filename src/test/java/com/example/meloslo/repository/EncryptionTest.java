package com.example.meloslo.repository;

import com.example.meloslo.model.OpenSlo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.meloslo.util.EncryptionConverter;
import com.example.meloslo.util.EncryptionUtils;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class EncryptionTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OpenSloRepository repository;

    @BeforeEach
    void setUp() {
        // The key state is static and @DataJpaTest does not load the EncryptionUtils bean:
        // start from the per-JVM default key.
        EncryptionUtils.configure("", "", false);
    }

    @Test
    void testOpenSloFieldsAreEncryptedInDatabase() {
        String originalSpec = "apiVersion: openslo/v1\nkind: DataSource\nspec:\n  url: http://secret.com";
        String originalAlertUrl = "https://example.invalid/webhook/test";
        
        OpenSlo slo = new OpenSlo("openslo/v1", "DataSource", "secret-source", "Secret Source", originalSpec);
        slo.setAlertUrl(originalAlertUrl);
        
        OpenSlo saved = repository.save(slo);
        entityManager.flush();
        entityManager.clear();
        
        // Check using entity - should be decrypted
        OpenSlo found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSpec()).isEqualTo(originalSpec);
        assertThat(found.getAlertUrl()).isEqualTo(originalAlertUrl);
        
        // Check raw database content using JDBC - should be encrypted
        Map<String, Object> rawData = jdbcTemplate.queryForMap(
            "SELECT spec, alert_url FROM openslo_records WHERE id = ?", saved.getId());
        
        String rawSpec = (String) rawData.get("spec");
        String rawAlertUrl = (String) rawData.get("alert_url");
        
        assertThat(rawSpec).isNotEqualTo(originalSpec);
        assertThat(rawAlertUrl).isNotEqualTo(originalAlertUrl);
        
        // Verify we can decrypt it back using the converter
        assertThat(EncryptionUtils.decrypt(rawSpec)).isEqualTo(originalSpec);
        assertThat(EncryptionUtils.decrypt(rawAlertUrl)).isEqualTo(originalAlertUrl);
    }
}
