package com.example.meloslo.service;

import com.example.meloslo.model.SliMetric;

import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Shared SLO calculation rules used by the dashboard/API and the PDF report.
 * A null value always means "no data"; it is never replaced by 0 or 100%.
 */
public final class SloMath {
    public static final String HEALTHY = "Healthy";
    public static final String WARNING = "Warning";
    public static final String BREACHING = "Breaching";
    public static final String NO_DATA = "No data";
    public static final String UNKNOWN = "Unknown";

    private SloMath() {
    }

    /** Mean of the non-null sample values; null if there are none. */
    public static Double average(Collection<SliMetric> metrics) {
        if (metrics == null) return null;
        double sum = 0;
        int count = 0;
        for (SliMetric m : metrics) {
            if (m != null && m.getValue() != null) {
                sum += m.getValue();
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    /**
     * (value - target) / (100 - target) * 100 on the 0 to 100 scale; null if there is no value
     * or the target is invalid (null).
     */
    public static Double errorBudget(Double value, Double target) {
        if (value == null || target == null) return null;
        return SliScale.errorBudget(value, target);
    }

    /** Status of an SLO: Unknown for an invalid target, No data without samples, else by error budget. */
    public static String status(Double errorBudget, Double target) {
        return target == null ? UNKNOWN : status(errorBudget);
    }

    public static String status(Double errorBudget) {
        if (errorBudget == null) return NO_DATA;
        if (errorBudget >= 25) return HEALTHY;
        if (errorBudget >= 0) return WARNING;
        return BREACHING;
    }

    /**
     * Average per bucket for every bucket from truncate(from) to truncate(to) inclusive.
     * Buckets without samples map to null. Samples without timestamp are ignored.
     */
    public static TreeMap<LocalDateTime, Double> bucketAverages(List<SliMetric> metrics, LocalDateTime from, LocalDateTime to,
                                                               UnaryOperator<LocalDateTime> truncate, TemporalUnit step) {
        TreeMap<LocalDateTime, double[]> acc = new TreeMap<>();
        for (SliMetric m : metrics) {
            if (m == null || m.getTimestamp() == null || m.getValue() == null) continue;
            double[] a = acc.computeIfAbsent(truncate.apply(m.getTimestamp()), k -> new double[2]);
            a[0] += m.getValue();
            a[1]++;
        }
        TreeMap<LocalDateTime, Double> result = new TreeMap<>();
        LocalDateTime current = truncate.apply(from);
        LocalDateTime end = truncate.apply(to);
        while (!current.isAfter(end)) {
            double[] a = acc.get(current);
            result.put(current, a == null ? null : a[0] / a[1]);
            current = current.plus(1, step);
        }
        return result;
    }
}
