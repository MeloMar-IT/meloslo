package com.example.meloslo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.security.core.SpringSecurityCoreVersion;

/** Guards against a downgrade of the Spring stack (minimum versions only). */
class DependencyBaselineTest {

    @Test
    void springBootIsAtLeast3_5() {
        assertThat(atLeast(SpringBootVersion.getVersion(), 3, 5)).isTrue();
    }

    @Test
    void springFrameworkIsAtLeast6_2() {
        assertThat(atLeast(SpringVersion.getVersion(), 6, 2)).isTrue();
    }

    @Test
    void springSecurityIsAtLeast6_5() {
        assertThat(atLeast(SpringSecurityCoreVersion.getVersion(), 6, 5)).isTrue();
    }

    @Test
    void helperComparesVersions() {
        assertThat(atLeast("3.5.7", 3, 5)).isTrue();
        assertThat(atLeast("4.0.0", 3, 5)).isTrue();
        assertThat(atLeast("3.2.0", 3, 5)).isFalse();
        assertThat(atLeast("3.4.9", 3, 5)).isFalse();
    }

    static boolean atLeast(String version, int major, int minor) {
        assertThat(version).as("version must be known").isNotNull();
        String[] parts = version.split("[.\\-]");
        int actualMajor = Integer.parseInt(parts[0]);
        int actualMinor = Integer.parseInt(parts[1]);
        return actualMajor > major || (actualMajor == major && actualMinor >= minor);
    }
}
