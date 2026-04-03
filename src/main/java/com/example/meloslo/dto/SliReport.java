package com.example.meloslo.dto;

import com.example.meloslo.model.SliMetric;
import java.util.List;

public class SliReport {
    private String name;
    private String displayName;
    private Double currentValue;
    private Double target;
    private List<SliMetric> recentMetrics;

    public SliReport() {
    }

    public SliReport(String name, String displayName, Double currentValue, List<SliMetric> recentMetrics) {
        this(name, displayName, currentValue, null, recentMetrics);
    }

    public SliReport(String name, String displayName, Double currentValue, Double target, List<SliMetric> recentMetrics) {
        this.name = name;
        this.displayName = displayName;
        this.currentValue = currentValue;
        this.target = target;
        this.recentMetrics = recentMetrics;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentValue) {
        this.currentValue = currentValue;
    }

    public Double getTarget() {
        return target;
    }

    public void setTarget(Double target) {
        this.target = target;
    }

    public List<SliMetric> getRecentMetrics() {
        return recentMetrics;
    }

    public void setRecentMetrics(List<SliMetric> recentMetrics) {
        this.recentMetrics = recentMetrics;
    }
}
