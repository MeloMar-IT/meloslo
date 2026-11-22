package com.example.meloslo.dto;

import java.util.List;

public class ReportOptionsDTO {
    private List<Long> ids;
    private boolean includeBusinessServiceAggregatedChart = true;
    private boolean includeSloDetails = true; // Overall toggle for details section
    private boolean includeSloCharts = true;
    private boolean includeMetadata = true; // System ID, Dept, Manager
    
    // Time period options
    private int timeValue = 1;
    private String timeUnit = "YEARS"; // YEARS, MONTHS, DAYS

    // More granular options for SLO Details
    private boolean includeTarget = true;
    private boolean includeAvgPerformance = true;
    private boolean includeErrorBudget = true;
    private boolean includeStatus = true;

    // Chart toggles
    private boolean includeErrorBudgetChart = true;
    private boolean includeMetricsChart = true;

    // Getters and Setters
    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public boolean isIncludeBusinessServiceAggregatedChart() {
        return includeBusinessServiceAggregatedChart;
    }

    public void setIncludeBusinessServiceAggregatedChart(boolean includeBusinessServiceAggregatedChart) {
        this.includeBusinessServiceAggregatedChart = includeBusinessServiceAggregatedChart;
    }

    public boolean isIncludeSloDetails() {
        return includeSloDetails;
    }

    public void setIncludeSloDetails(boolean includeSloDetails) {
        this.includeSloDetails = includeSloDetails;
    }

    public boolean isIncludeSloCharts() {
        return includeSloCharts;
    }

    public void setIncludeSloCharts(boolean includeSloCharts) {
        this.includeSloCharts = includeSloCharts;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public int getTimeValue() {
        return timeValue;
    }

    public void setTimeValue(int timeValue) {
        this.timeValue = timeValue;
    }

    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isIncludeTarget() {
        return includeTarget;
    }

    public void setIncludeTarget(boolean includeTarget) {
        this.includeTarget = includeTarget;
    }

    public boolean isIncludeAvgPerformance() {
        return includeAvgPerformance;
    }

    public void setIncludeAvgPerformance(boolean includeAvgPerformance) {
        this.includeAvgPerformance = includeAvgPerformance;
    }

    public boolean isIncludeErrorBudget() {
        return includeErrorBudget;
    }

    public void setIncludeErrorBudget(boolean includeErrorBudget) {
        this.includeErrorBudget = includeErrorBudget;
    }

    public boolean isIncludeStatus() {
        return includeStatus;
    }

    public void setIncludeStatus(boolean includeStatus) {
        this.includeStatus = includeStatus;
    }

    public boolean isIncludeErrorBudgetChart() {
        return includeErrorBudgetChart;
    }

    public void setIncludeErrorBudgetChart(boolean includeErrorBudgetChart) {
        this.includeErrorBudgetChart = includeErrorBudgetChart;
    }

    public boolean isIncludeMetricsChart() {
        return includeMetricsChart;
    }

    public void setIncludeMetricsChart(boolean includeMetricsChart) {
        this.includeMetricsChart = includeMetricsChart;
    }
}
