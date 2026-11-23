package com.example.meloslo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks of shipped files: no committed key, no debug logging outside dev, the SPA's CSRF and masked-URL wiring. */
class StaticConfigSecurityTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = StaticConfigSecurityTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as("classpath resource " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Properties props(String name) throws IOException {
        Properties p = new Properties();
        try (InputStream in = StaticConfigSecurityTest.class.getClassLoader().getResourceAsStream(name)) {
            p.load(in);
        }
        return p;
    }

    @Test
    void noEncryptionKeyIsCommitted() throws IOException {
        assertThat(props("application.properties").getProperty("app.encryption.key"))
                .isEqualTo("${APP_ENCRYPTION_KEY:}");
        String utils = Files.readString(Path.of("src/main/java/com/example/meloslo/util/EncryptionUtils.java"));
        for (String text : List.of(resource("application.properties"), resource("application-dev.properties"),
                resource("application-prod.properties"), utils)) {
            assertThat(text).doesNotContain("ThisIsASecretK16").doesNotContain("DefaultKey16Byte");
        }
    }

    @Test
    void baseAndProdPropertiesSetNoDebugOrTraceForHibernateOrSecurity() throws IOException {
        for (String file : List.of("application.properties", "application-prod.properties")) {
            Properties p = props(file);
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith("logging.level.org.hibernate") || name.startsWith("logging.level.org.springframework.security")) {
                    assertThat(p.getProperty(name).toUpperCase()).as(file + " " + name).isNotIn("DEBUG", "TRACE");
                }
            }
        }
        assertThat(props("application-prod.properties").getProperty("app.encryption.require-key")).isEqualTo("true");
    }

    @Test
    void csrfHelperAndMaskedUrlFlowArePresentInTheSpa() throws IOException {
        String html = resource("static/index.html");
        assertThat(html).contains("decodeURIComponent(m[1])").doesNotContain("[removed");
        int input = html.indexOf("<input v-else type=\"url\" v-model=\"form.alertUrl\"");
        assertThat(input).isGreaterThan(0);
        assertThat(html.substring(Math.max(0, input - 1500), input)).contains("replaceAlertUrl");
        assertThat(html).doesNotContain("hooks.slack.com/services/T0");
    }
}
