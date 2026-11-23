package com.example.meloslo;

import com.example.meloslo.model.User;
import com.example.meloslo.util.EncryptionUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:prodprofiletest", "app.encryption.require-key=false"})
@ActiveProfiles("prod")
@ExtendWith(OutputCaptureExtension.class)
class ProdProfileSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlAndSecurityDebugLoggingIsOff() {
        assertThat(LoggerFactory.getLogger("org.hibernate.SQL").isDebugEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.hibernate.orm.jdbc.bind").isTraceEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.hibernate.type.descriptor.sql.BasicBinder").isTraceEnabled()).isFalse();
        assertThat(LoggerFactory.getLogger("org.springframework.security").isDebugEnabled()).isFalse();
    }

    @Test
    void savingAUserLogsNoHashAndNoBoundParameters(CapturedOutput output) {
        userRepository.save(new User("logcheck", passwordEncoder.encode("a-long-enough-password"),
                "logcheck@example.com", "Finance"));

        assertThat(output.getAll()).doesNotContain("$2a$").doesNotContain("binding parameter");
    }

    @Test
    void prodStartupFailsWithoutEncryptionKey() {
        assertStartupFailsNamingKey("prod", "jdbc:h2:mem:nokey");
    }

    @Test
    void persistentDatasourceStartupFailsWithoutEncryptionKey() {
        assertStartupFailsNamingKey("default", "jdbc:h2:file:" + tempDir.resolve("nokey").toAbsolutePath());
    }

    private static void assertStartupFailsNamingKey(String profile, String url) {
        ConfigurableApplicationContext ctx = null;
        try {
            ctx = new SpringApplicationBuilder(MeloSloApplication.class)
                    .profiles(profile)
                    .web(WebApplicationType.NONE)
                    .run("--spring.datasource.url=" + url, "--app.encryption.key=", "--app.encryption.legacy-keys=",
                            "--app.encryption.require-key=" + "prod".equals(profile));
            fail("The context must not start without an encryption key");
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            assertThat(root.getMessage()).contains("APP_ENCRYPTION_KEY");
        } finally {
            if (ctx != null) ctx.close();
            // A failed start must not leave a half configured key behind
            EncryptionUtils.configure("", "", false);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void consoleIsNotFoundForAnonymous() {
        assertThat(restTemplate.getForEntity("/h2-console", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity("/h2-console/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void liveStartCreatesNoTestuserAndNoDefaultAdminPassword() {
        assertThat(userRepository.findByUsername("testuser")).isEmpty();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(passwordEncoder.matches("admin", admin.getPassword())).isFalse();
    }

    @Test
    void defaultAdminCredentialsAreRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("admin", "admin");
        headers.add("X-Requested-With", "XMLHttpRequest");

        var response = restTemplate.exchange("/api/v1/users/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
