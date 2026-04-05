package com.example.meloslo.dto;

public class DashboardStats {
    private long totalBusinessServices;
    private long totalSlos;
    private long healthySlos;
    private long warningSlos;
    private long breachingSlos;

    public DashboardStats() {}

    public DashboardStats(long totalServices, long totalSlos, long healthySlos, long warningSlos, long breachingSlos) {
        this.totalBusinessServices = totalServices;
        this.totalSlos = totalSlos;
        this.healthySlos = healthySlos;
        this.warningSlos = warningSlos;
        this.breachingSlos = breachingSlos;
    }

    public long getTotalBusinessServices() {
        return totalBusinessServices;
    }

    public void setTotalBusinessServices(long totalServices) {
        this.totalBusinessServices = totalServices;
    }

    public long getTotalSlos() {
        return totalSlos;
    }

    public void setTotalSlos(long totalSlos) {
        this.totalSlos = totalSlos;
    }

    public long getHealthySlos() {
        return healthySlos;
    }

    public void setHealthySlos(long healthySlos) {
        this.healthySlos = healthySlos;
    }

    public long getWarningSlos() {
        return warningSlos;
    }

    public void setWarningSlos(long warningSlos) {
        this.warningSlos = warningSlos;
    }

    public long getBreachingSlos() {
        return breachingSlos;
    }

    public void setBreachingSlos(long breachingSlos) {
        this.breachingSlos = breachingSlos;
    }
}
