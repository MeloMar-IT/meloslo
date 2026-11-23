package com.example.meloslo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertUrlValidatorTest {

    /** Fixed host table; IP literals resolve to themselves. No DNS is used. */
    private static AlertUrlValidator.HostResolver fakeDns() {
        Map<String, String[]> table = Map.of(
                "hooks.example.test", new String[] {"93.184.216.34"},
                "events.pagerduty.com", new String[] {"93.184.216.34"},
                "evilpagerduty.com", new String[] {"93.184.216.34"},
                "discord.com", new String[] {"93.184.216.34"},
                "mixed.example.test", new String[] {"93.184.216.34", "10.0.0.5"});
        return host -> {
            String[] mapped = table.get(host);
            if (mapped != null) {
                InetAddress[] out = new InetAddress[mapped.length];
                for (int i = 0; i < mapped.length; i++) out[i] = InetAddress.getByName(mapped[i]);
                return out;
            }
            if (host.matches("[0-9.]+") || host.contains(":")) {
                return InetAddress.getAllByName(host);
            }
            throw new UnknownHostException(host);
        };
    }

    private final AlertUrlValidator validator = new AlertUrlValidator("", fakeDns());

    @Test
    void nullBlankAndPublicUrlsPass() {
        assertThatCode(() -> validator.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("  ")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("https://93.184.216.34/hook")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("https://hooks.example.test/hook")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/x", "http://10.1.2.3/x", "http://172.16.0.1/x", "http://192.168.1.1/x",
            "http://169.254.169.254/x", "http://0.0.0.0/x", "http://100.64.0.1/x", "http://192.0.2.1/x",
            "http://198.51.100.7/x", "http://203.0.113.9/x", "http://198.18.0.1/x", "http://240.0.0.1/x",
            "http://255.255.255.255/x", "http://[::1]/x", "http://[fd00::1]/x", "http://[fe80::1]/x",
            "http://[2001:db8::1]/x", "http://[::ffff:127.0.0.1]/x", "http://[::ffff:192.0.2.1]/x",
            "http://[64:ff9b::7f00:1]/x", "http://[64:ff9b::c000:201]/x", "http://[2002:7f00:1::1]/x"})
    void forbiddenAddressesAreRefused(String url) {
        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Alert URL points to a private or internal address");
    }

    @Test
    void hostResolvingToPublicAndPrivateAddressIsRefused() {
        assertThatThrownBy(() -> validator.validate("https://mixed.example.test/x"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("private or internal");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://hooks.example.test/x", "file:///etc/passwd", "mailto:a@example.invalid",
            "https:///nohost", "https://unknown.example.test/x", "https://hooks.example.test/****", "****", "not a url"})
    void badSchemesMissingHostsUnresolvableAndMaskedValuesAreRefused(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void allowListMatchesExactHostAndSubdomainsOnly() {
        AlertUrlValidator listed = new AlertUrlValidator("pagerduty.com, hooks.example.test", fakeDns());

        assertThatCode(() -> listed.validate("https://events.pagerduty.com/v2/enqueue")).doesNotThrowAnyException();
        assertThatCode(() -> listed.validate("https://HOOKS.EXAMPLE.TEST./x")).doesNotThrowAnyException();
        assertThatThrownBy(() -> listed.validate("https://evilpagerduty.com/x"))
                .isInstanceOf(BadRequestException.class).hasMessage("Alert URL host is not allowed");
        assertThatThrownBy(() -> listed.validate("https://discord.com/x"))
                .isInstanceOf(BadRequestException.class).hasMessage("Alert URL host is not allowed");
    }

    @Test
    void messagesNeverContainThePath() {
        assertThatThrownBy(() -> validator.validate("http://10.0.0.5/SECRETPATH"))
                .hasMessageNotContaining("SECRETPATH");
        assertThatThrownBy(() -> validator.validate("ftp://hooks.example.test/SECRETPATH"))
                .hasMessageNotContaining("SECRETPATH");
        assertThatThrownBy(() -> validator.validate("https://unknown.example.test/SECRETPATH"))
                .hasMessageNotContaining("SECRETPATH");
    }

    @Test
    void hostOfReturnsOnlyTheHost() {
        assertThat(AlertUrlValidator.hostOf("https://hooks.example.test/SECRETPATH?x=1")).isEqualTo("hooks.example.test");
        assertThat(AlertUrlValidator.hostOf("mailto:a@example.invalid")).isEqualTo("<unparsable>");
        assertThat(AlertUrlValidator.hostOf("::: bad")).isEqualTo("<unparsable>");
    }

    @Test
    void springCreatesTheBeanThroughTheAutowiredConstructor() {
        new ApplicationContextRunner()
                .withBean(AlertUrlValidator.class)
                .withPropertyValues("app.alerting.allowed-hosts=hooks.example.test")
                .run(ctx -> assertThat(ctx).hasSingleBean(AlertUrlValidator.class));
    }
}
