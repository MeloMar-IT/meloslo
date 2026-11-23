package com.example.meloslo.config;

import com.example.meloslo.util.EncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opt-in: rewrites every encrypted column of {@code openslo_records} under the current key.
 * Each row is locked and read fresh inside its own transaction, so concurrent edits are never
 * overwritten with stale data. Values no configured key can decrypt are left untouched.
 */
@Component
@ConditionalOnProperty(name = "app.encryption.reencrypt-on-startup", havingValue = "true")
public class EncryptionMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EncryptionMigration.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public EncryptionMigration(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        reencryptAll();
    }

    /** @return the number of rows rewritten */
    public int reencryptAll() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM openslo_records", Long.class);
        int rewritten = 0;
        int skipped = 0;
        for (Long id : ids) {
            Boolean done = rewriteRow(id);
            if (done == null) continue;
            if (done) rewritten++; else skipped++;
        }
        log.info("Encryption migration finished: rewritten={}, skipped={}", rewritten, skipped);
        if (skipped > 0) {
            log.warn("{} record(s) hold values that no configured key can decrypt and were left unchanged", skipped);
        }
        return rewritten;
    }

    /** @return true if rewritten, false if skipped (undecryptable), null if the row no longer exists */
    Boolean rewriteRow(long id) {
        return tx.execute(status -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT spec, alert_url, alert_payload FROM openslo_records WHERE id = ? FOR UPDATE", id);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = rows.get(0);
            String[] cols = {"spec", "alert_url", "alert_payload"};
            String[] plain = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                String raw = columnAsString(row.get(cols[i]));
                if (raw == null) continue;
                Optional<String> d = EncryptionUtils.tryDecrypt(raw);
                if (d.isEmpty()) return false;
                plain[i] = d.get();
            }
            jdbc.update("UPDATE openslo_records SET spec = ?, alert_url = ?, alert_payload = ? WHERE id = ?",
                EncryptionUtils.encrypt(plain[0]), EncryptionUtils.encrypt(plain[1]),
                EncryptionUtils.encrypt(plain[2]), id);
            return true;
        });
    }

    private static String columnAsString(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Cannot read column", e);
            }
        }
        return value.toString();
    }
}
