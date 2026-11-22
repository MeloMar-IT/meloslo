package com.example.meloslo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SliScaleTest {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    private OptionalDouble target(String objectivesYaml) {
        return SliScale.targetFromYaml(yaml, "spec:\n  objectives:\n" + objectivesYaml);
    }

    @Test
    void errorBudgetMatchesHandComputedValues() {
        assertThat(SliScale.errorBudget(99.5, 99.0)).isCloseTo(50.0, within(1e-9));
        assertThat(SliScale.errorBudget(99.6, 99.0)).isCloseTo(60.0, within(1e-9));
        assertThat(SliScale.errorBudget(95.0, 95.0)).isCloseTo(0.0, within(1e-9));
        assertThat(SliScale.errorBudget(95.5, 95.0)).isCloseTo(10.0, within(1e-9));
        assertThat(SliScale.errorBudget(94.0, 95.0)).isCloseTo(-20.0, within(1e-9));
        assertThat(SliScale.errorBudget(99.95, 99.9)).isCloseTo(50.0, within(1e-9));
        assertThat(SliScale.errorBudget(99.8, 99.9)).isCloseTo(-100.0, within(1e-9));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 100.0, 150.0})
    void errorBudgetRejectsTargetsOutsideOpenInterval(double badTarget) {
        assertThatThrownBy(() -> SliScale.errorBudget(99.0, badTarget)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 100.0, 50.5})
    void requireValidValueAcceptsRange(double v) {
        assertThat(SliScale.requireValidValue(v)).isEqualTo(v);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 100.1, Double.NaN})
    void requireValidValueRejectsOutOfRange(double v) {
        assertThatThrownBy(() -> SliScale.requireValidValue(v)).isInstanceOf(InvalidSliValueException.class);
    }

    @Test
    void requireValidValueRejectsNull() {
        assertThatThrownBy(() -> SliScale.requireValidValue(null)).isInstanceOf(InvalidSliValueException.class);
    }

    @Test
    void fractionTargetIsConvertedExactly() {
        assertThat(target("    - target: 0.999\n").getAsDouble()).isEqualTo(99.9);
    }

    @Test
    void targetPercentIsReadAsIs() {
        assertThat(target("    - targetPercent: 99.9\n").getAsDouble()).isEqualTo(99.9);
    }

    @Test
    void ratioMetricsTargetIsReadLikeTarget() {
        assertThat(target("    - ratioMetrics:\n        target: 0.95\n").getAsDouble()).isEqualTo(95.0);
    }

    @Test
    void numericTextIsAccepted() {
        assertThat(target("    - target: \"0.99\"\n").getAsDouble()).isEqualTo(99.0);
    }

    @Test
    void missingTargetsGiveEmpty() {
        assertThat(SliScale.targetFromYaml(yaml, "spec: {}")).isEmpty();
        assertThat(target("    - displayName: x\n")).isEmpty();
        assertThat(target("    - target: null\n")).isEmpty();
        assertThat(SliScale.targetFromYaml(yaml, null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "    - target: 99.9\n",
            "    - target: 1\n",
            "    - target: 0\n",
            "    - target: -0.5\n",
            "    - target: abc\n",
            "    - targetPercent: 100\n",
            "    - targetPercent: 0\n",
            "    - target: 0.99\n      targetPercent: 99\n",
            "    - target: 0.99\n    - target: 99.9\n",
            "    - target: 0.99\n      ratioMetrics:\n        target: 1.5\n"
    })
    void invalidTargetsAreRejected(String objectives) {
        assertThatThrownBy(() -> target(objectives)).isInstanceOf(InvalidSliValueException.class);
    }
}
