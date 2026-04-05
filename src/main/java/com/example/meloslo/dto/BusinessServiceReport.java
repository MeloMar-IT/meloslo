package com.example.meloslo.dto;

import java.util.List;

public class BusinessServiceReport {
    private String serviceName;
    private String displayName;
    private List<SloReport> sloReports;

    public BusinessServiceReport() {}

    public BusinessServiceReport(String serviceName, String displayName, List<SloReport> sloReports) {
        this.serviceName = serviceName;
        this.displayName = displayName;
        this.sloReports = sloReports;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<SloReport> getSloReports() {
        return sloReports;
    }

    public void setSloReports(List<SloReport> sloReports) {
        this.sloReports = sloReports;
    }
}
