package com.example.meloslo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Scale rules for SLI values and SLO targets. Values, targets and current values are on a 0 to 100 scale.
 * OpenSLO "target" fields are fractions and are converted when a spec is read.
 */
public final class SliScale {
    /** Lowest valid SLI value and SLO target on the 0 to 100 scale. */
    public static final double MIN_VALUE = 0.0;
    /** Highest valid SLI value on the 0 to 100 scale. */
    public static final double MAX_VALUE = 100.0;
    /** Target (99%) used when an SLO specification sets no objective target. */
    public static final double DEFAULT_TARGET = 99.0;

    private static final String TARGET_HINT =
            "SLO target must be a fraction strictly between 0 and 1 (e.g. 0.999) or use targetPercent strictly between 0 and 100";

    private SliScale() {
    }

    /**
     * Returns the value if it is a number on the 0 to 100 scale (inclusive).
     *
     * @param value the SLI metric value; null, NaN and infinite values are refused
     * @return the same value
     * @throws InvalidSliValueException when the value is missing or outside the scale
     */
    public static double requireValidValue(Double value) {
        if (value == null || value.isNaN() || value < MIN_VALUE || value > MAX_VALUE) {
            throw new InvalidSliValueException("SLI metric value must be between 0 and 100, got " + value);
        }
        return value;
    }

    /** Error budget remaining in percent: (value - target) / (100 - target) * 100. */
    public static double errorBudget(double value, double target) {
        if (!(target > 0 && target < MAX_VALUE)) {
            throw new IllegalArgumentException("Target must be strictly between 0 and 100, got " + target);
        }
        return (value - target) / (MAX_VALUE - target) * 100.0;
    }

    /** Like targetFromSpec for YAML text; blank or unparsable YAML gives an empty result. */
    public static OptionalDouble targetFromYaml(ObjectMapper yamlMapper, String spec) {
        if (spec == null || spec.isBlank()) {
            return OptionalDouble.empty();
        }
        JsonNode root;
        try {
            root = yamlMapper.readTree(spec);
        } catch (Exception e) {
            return OptionalDouble.empty();
        }
        return targetFromSpec(root);
    }

    /**
     * Validates every objective of the spec and returns the target (0 to 100) of the first objective.
     *
     * @param specRoot parsed spec document (the node holding the "spec" field)
     * @throws InvalidSliValueException when a target is not numeric, out of range, or several target fields are set
     */
    public static OptionalDouble targetFromSpec(JsonNode specRoot) {
        if (specRoot == null) {
            return OptionalDouble.empty();
        }
        JsonNode objectives = specRoot.path("spec").path("objectives");
        if (!objectives.isArray()) {
            return OptionalDouble.empty();
        }
        OptionalDouble first = OptionalDouble.empty();
        for (int i = 0; i < objectives.size(); i++) {
            OptionalDouble t = objectiveTarget(objectives.get(i));
            if (i == 0) {
                first = t;
            }
        }
        return first;
    }

    private static OptionalDouble objectiveTarget(JsonNode objective) {
        if (objective == null || !objective.isObject()) {
            throw new InvalidSliValueException("Each SLO objective must be an object");
        }
        List<String> present = new ArrayList<>();
        JsonNode percent = pick(objective.path("targetPercent"), "targetPercent", present);
        JsonNode fraction = pick(objective.path("target"), "target", present);
        JsonNode ratio = pick(objective.path("ratioMetrics").path("target"), "ratioMetrics.target", present);
        if (present.size() > 1) {
            throw new InvalidSliValueException(
                    "An SLO objective must set only one of target, targetPercent, ratioMetrics.target; got " + present);
        }
        if (percent != null) {
            double v = parse(percent, "targetPercent");
            if (!(v > 0 && v < MAX_VALUE)) {
                throw new InvalidSliValueException(TARGET_HINT + "; got targetPercent " + percent.asText());
            }
            return OptionalDouble.of(v);
        }
        JsonNode node = fraction != null ? fraction : ratio;
        if (node == null) {
            return OptionalDouble.empty();
        }
        String field = fraction != null ? "target" : "ratioMetrics.target";
        double v = parse(node, field);
        if (!(v > 0 && v < 1.0)) {
            throw new InvalidSliValueException(TARGET_HINT + "; got " + node.asText());
        }
        return OptionalDouble.of(BigDecimal.valueOf(v).movePointRight(2).doubleValue());
    }

    private static JsonNode pick(JsonNode node, String name, List<String> present) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        present.add(name);
        return node;
    }

    private static double parse(JsonNode node, String field) {
        double v;
        if (node.isNumber()) {
            v = node.asDouble();
        } else if (node.isTextual()) {
            try {
                v = Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException e) {
                throw new InvalidSliValueException("SLO " + field + " must be a number, got '" + node.asText() + "'");
            }
        } else {
            throw new InvalidSliValueException("SLO " + field + " must be a number, got " + node);
        }
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new InvalidSliValueException("SLO " + field + " must be a finite number, got " + node.asText());
        }
        return v;
    }
}
