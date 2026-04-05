package com.example.meloslo.dto;

import com.example.meloslo.model.SliMetric;
import java.util.List;

public class SloReport {
    private String name;
    private Double target;
    private Double currentValue;
    private Double errorBudget;
    private String status;
    private List<SliMetric> recentMetrics;
    private List<Double> trendPoints;

    public SloReport() {
    }

    public SloReport(String name, Double target, Double currentValue, Double errorBudget, String status, List<SliMetric> recentMetrics) {
        this(name, target, currentValue, errorBudget, status, recentMetrics, null);
    }

    public SloReport(String name, Double target, Double currentValue, Double errorBudget, String status, List<SliMetric> recentMetrics, List<Double> trendPoints) {
        this.name = name;
        this.target = target;
        this.currentValue = currentValue;
        this.errorBudget = errorBudget;
        this.status = status;
        this.recentMetrics = recentMetrics;
        this.trendPoints = trendPoints;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getTarget() {
        return target;
    }

    public void setTarget(Double target) {
        this.target = target;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentSlo) {
        this.currentValue = currentSlo;
    }

    public Double getErrorBudget() {
        return errorBudget;
    }

    public void setErrorBudget(Double errorBudget) {
        this.errorBudget = errorBudget;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SliMetric> getRecentMetrics() {
        return recentMetrics;
    }

    public void setRecentMetrics(List<SliMetric> recentMetrics) {
        this.recentMetrics = recentMetrics;
    }

    public List<Double> getTrendPoints() {
        return trendPoints;
    }

    public void setTrendPoints(List<Double> trendPoints) {
        this.trendPoints = trendPoints;
    }
}
