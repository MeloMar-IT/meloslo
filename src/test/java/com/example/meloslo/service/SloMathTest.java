package com.example.meloslo.service;

import com.example.meloslo.model.SliMetric;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class SloMathTest {

    private static SliMetric metric(LocalDateTime ts, Double value) {
        return new SliMetric(ts, value, null, null);
    }

    @Test
    void averageOfNothingIsNull() {
        assertNull(SloMath.average(List.of()));
        assertNull(SloMath.average(null));
        assertNull(SloMath.average(List.of(metric(LocalDateTime.now(), null))));
    }

    @Test
    void averageIsPlainMeanAndCountsSamplesWithoutTimestamp() {
        assertEquals(0.95, SloMath.average(List.of(metric(LocalDateTime.now(), 0.9), metric(null, 1.0))), 1e-9);
    }

    @Test
    void errorBudgetHandlesNullValueAndInvalidTarget() {
        assertNull(SloMath.errorBudget(null, 95.0));
        assertNull(SloMath.errorBudget(94.0, null));
        assertEquals(-20.0, SloMath.errorBudget(94.0, 95.0), 1e-9);
    }

    @Test
    void invalidTargetGivesUnknownStatus() {
        assertEquals(SloMath.UNKNOWN, SloMath.status(null, null));
        assertEquals(SloMath.NO_DATA, SloMath.status(null, 95.0));
        assertEquals(SloMath.HEALTHY, SloMath.status(30.0, 95.0));
    }

    @Test
    void statusThresholds() {
        assertEquals(SloMath.HEALTHY, SloMath.status(30.0));
        assertEquals(SloMath.WARNING, SloMath.status(10.0));
        assertEquals(SloMath.BREACHING, SloMath.status(-1.0));
        assertEquals("No data", SloMath.status(null));
    }

    @Test
    void bucketAveragesLeavesEmptyBucketsNull() {
        LocalDateTime d3 = LocalDateTime.of(2026, 3, 3, 12, 0);
        LocalDateTime d1 = d3.minusDays(2);
        TreeMap<LocalDateTime, Double> result = SloMath.bucketAverages(
                List.of(metric(d1.withHour(1), 0.9), metric(d1.withHour(2), 1.0), metric(null, 0.1)),
                d1, d3, t -> t.truncatedTo(ChronoUnit.DAYS), ChronoUnit.DAYS);

        assertEquals(3, result.size());
        assertEquals(0.95, result.get(d1.truncatedTo(ChronoUnit.DAYS)), 1e-9);
        assertNull(result.get(d1.plusDays(1).truncatedTo(ChronoUnit.DAYS)));
        assertNull(result.get(d3.truncatedTo(ChronoUnit.DAYS)));
        assertTrue(result.containsKey(d3.truncatedTo(ChronoUnit.DAYS)));
    }

    @Test
    void bucketAveragesSupportsWeeks() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 2, 10, 0); // a Monday
        LocalDateTime to = from.plusWeeks(2);
        java.util.function.UnaryOperator<LocalDateTime> weekStart = t ->
                t.truncatedTo(ChronoUnit.DAYS).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        TreeMap<LocalDateTime, Double> result = SloMath.bucketAverages(
                List.of(metric(from.plusDays(2), 0.8)), from, to, weekStart, ChronoUnit.WEEKS);

        assertEquals(3, result.size());
        assertEquals(0.8, result.firstEntry().getValue(), 1e-9);
        assertNull(result.lastEntry().getValue());
    }
}
