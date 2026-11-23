package com.example.meloslo.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** Time unit of a report period, with the largest accepted value (a 10-year period). */
public enum ReportTimeUnit {
    DAYS(3650),
    MONTHS(120),
    YEARS(10);

    private final int maxValue;

    ReportTimeUnit(int maxValue) {
        this.maxValue = maxValue;
    }

    public int maxValue() {
        return maxValue;
    }

    /** Parses a unit in any letter case; null stays null. */
    @JsonCreator
    public static ReportTimeUnit fromJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("timeUnit must be one of DAYS, MONTHS, YEARS", e);
        }
    }
}
