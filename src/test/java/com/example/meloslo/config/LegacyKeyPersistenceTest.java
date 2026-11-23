package com.example.meloslo.config;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.util.EncryptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keys are static, and the migration commits through its own transactions, so these tests run
 * without an outer transaction and delete the rows they create.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LegacyKeyPersistenceTest {

    private static final String KEY_A = "AAAAAAAAAAAAAAAA";
    private static final String KEY_B = "BBBBBBBBBBBBBBBB";
    private static final String KEY_C = "CCCCCCCCCCCCCCCC";
    private static final String SPEC = "apiVersion: openslo/v1\nkind: AlertingSource\nspec: {}";
    private static final String URL = "https://example.invalid/webhook/test";
    private static final String PAYLOAD = "{\"text\":\"hello\"}";

    @Autowired
    private OpenSloRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @BeforeEach
    @AfterEach
    void resetKeysAndRows() {
        EncryptionUtils.configure("", "", false);
        repository.deleteAll();
    }

    private Long saveUnderKey(String key, String name) {
        EncryptionUtils.configure(key, "", true);
        OpenSlo r = new OpenSlo("openslo/v1", "AlertingSource", name, name, SPEC);
        r.setAlertUrl(URL);
        r.setAlertPayload(PAYLOAD);
        return repository.saveAndFlush(r).getId();
    }

    private void assertLoadsWithOriginalValues(Long id) {
        OpenSlo found = repository.findById(id).orElseThrow();
        assertThat(found.getSpec()).isEqualTo(SPEC);
        assertThat(found.getAlertUrl()).isEqualTo(URL);
        assertThat(found.getAlertPayload()).isEqualTo(PAYLOAD);
    }

    private String raw(String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM openslo_records WHERE id = ?", String.class, id);
    }

    private EncryptionMigration migration() {
        return new EncryptionMigration(jdbc, txManager);
    }

    @Test
    void rowsWrittenUnderLegacyKeyLoadWithCurrentKey() {
        Long id = saveUnderKey(KEY_A, "legacy-load");

        EncryptionUtils.configure(KEY_B, KEY_A, true);

        assertLoadsWithOriginalValues(id);
    }

    @Test
    void migrationMakesRowsReadableWithTheCurrentKeyAlone() {
        Long id = saveUnderKey(KEY_A, "migrated");
        EncryptionUtils.configure(KEY_B, KEY_A, true);

        int rewritten = migration().reencryptAll();

        assertThat(rewritten).isGreaterThanOrEqualTo(1);
        EncryptionUtils.configure(KEY_B, "", true);
        assertLoadsWithOriginalValues(id);
    }

    @Test
    void plainSaveOfAnUnchangedEntityDoesNotReencrypt() {
        Long id = saveUnderKey(KEY_A, "plain-save");
        EncryptionUtils.configure(KEY_B, KEY_A, true);
        OpenSlo found = repository.findById(id).orElseThrow();

        repository.saveAndFlush(found);

        EncryptionUtils.configure(KEY_B, "", true);
        assertThat(repository.findById(id).orElseThrow().getSpec()).isNotEqualTo(SPEC);
    }

    @Test
    void valuesNoKeyCanDecryptAreLeftByteForByteUnchanged() {
        Long id = saveUnderKey(KEY_C, "unknown-key");
        String specBefore = raw("spec", id);
        String urlBefore = raw("alert_url", id);
        String payloadBefore = raw("alert_payload", id);
        EncryptionUtils.configure(KEY_B, KEY_A, true);

        migration().reencryptAll();

        assertThat(raw("spec", id)).isEqualTo(specBefore);
        assertThat(raw("alert_url", id)).isEqualTo(urlBefore);
        assertThat(raw("alert_payload", id)).isEqualTo(payloadBefore);
    }

    @Test
    void rowIsRewrittenFromFreshValuesNotFromAnEarlierRead() {
        Long id = saveUnderKey(KEY_A, "concurrent");
        EncryptionUtils.configure(KEY_B, KEY_A, true);
        // A writer commits between the id listing and the row lock
        jdbc.update("UPDATE openslo_records SET spec = ? WHERE id = ?", EncryptionUtils.encrypt("fresh spec"), id);

        Boolean done = migration().rewriteRow(id);

        assertThat(done).isTrue();
        EncryptionUtils.configure(KEY_B, "", true);
        assertThat(EncryptionUtils.decrypt(raw("spec", id))).isEqualTo("fresh spec");
        assertThat(EncryptionUtils.decrypt(raw("alert_url", id))).isEqualTo(URL);
    }

    @Test
    void rowDeletedBeforeTheLockIsSkipped() {
        Long id = saveUnderKey(KEY_A, "deleted");
        EncryptionUtils.configure(KEY_B, KEY_A, true);
        repository.deleteById(id);

        assertThat(migration().rewriteRow(id)).isNull();
    }
}
