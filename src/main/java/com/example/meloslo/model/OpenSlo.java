package com.example.meloslo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.example.meloslo.util.EncryptionConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "openslo_records")
public class OpenSlo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "api_version")
    private String apiVersion;

    @NotBlank
    private String kind;

    @NotBlank
    @Column(unique = true)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @NotBlank
    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String spec;

    private String department;

    private String manager; // Manager
    
    private Integer refreshRate = 60; // Default 60 minutes
    
    private LocalDateTime lastRefreshTime;

    @Convert(converter = EncryptionConverter.class)
    private String alertUrl;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EncryptionConverter.class)
    private String alertPayload;

    private LocalDateTime lastAlertTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    @JsonIgnoreProperties({"slos", "slis", "indicatorSlis"})
    private OpenSlo service;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "datasource_id")
    @JsonIgnoreProperties({"slos", "slis", "indicatorSlis", "service"})
    private OpenSlo datasource;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "slo_slis",
        joinColumns = @JoinColumn(name = "slo_id"),
        inverseJoinColumns = @JoinColumn(name = "sli_id")
    )
    @JsonIgnoreProperties({"slos", "slis", "service", "indicatorSlis", "datasource"})
    private List<OpenSlo> slis = new ArrayList<>();

    @OneToMany(mappedBy = "service", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"service", "slis", "indicatorSlis", "datasource"})
    private List<OpenSlo> slos = new ArrayList<>();

    @OneToMany(mappedBy = "datasource", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"datasource", "slis", "slos", "service"})
    private List<OpenSlo> indicatorSlis = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "alerting_source_id")
    @JsonIgnoreProperties({"slos", "slis", "indicatorSlis", "service", "datasource", "alertingSource"})
    private OpenSlo alertingSource;

    @OneToMany(mappedBy = "alertingSource", fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"alertingSource", "slis", "slos", "service", "datasource"})
    private List<OpenSlo> linkedSlos = new ArrayList<>();

    @Transient
    private Double errorBudget;

    @Transient
    private String status;

    @Transient
    private Double currentValue;

    public OpenSlo() {
    }

    public OpenSlo(String apiVersion, String kind, String name, String displayName, String spec) {
        this.apiVersion = apiVersion;
        this.kind = kind;
        this.name = name;
        this.displayName = displayName;
        this.spec = spec;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }

    public Integer getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(Integer refreshRate) {
        this.refreshRate = refreshRate;
    }

    public LocalDateTime getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(LocalDateTime lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public String getAlertUrl() {
        return alertUrl;
    }

    public void setAlertUrl(String alertUrl) {
        this.alertUrl = alertUrl;
    }

    public String getAlertPayload() {
        return alertPayload;
    }

    public void setAlertPayload(String alertPayload) {
        this.alertPayload = alertPayload;
    }

    public LocalDateTime getLastAlertTime() {
        return lastAlertTime;
    }

    public void setLastAlertTime(LocalDateTime lastAlertTime) {
        this.lastAlertTime = lastAlertTime;
    }

    public OpenSlo getAlertingSource() {
        return alertingSource;
    }

    public void setAlertingSource(OpenSlo alertingSource) {
        this.alertingSource = alertingSource;
    }

    public List<OpenSlo> getLinkedSlos() {
        return linkedSlos;
    }

    public void setLinkedSlos(List<OpenSlo> linkedSlos) {
        this.linkedSlos = linkedSlos;
    }

    public OpenSlo getService() {
        return service;
    }

    public void setService(OpenSlo service) {
        this.service = service;
    }

    public OpenSlo getDatasource() {
        return datasource;
    }

    public void setDatasource(OpenSlo datasource) {
        this.datasource = datasource;
    }

    public List<OpenSlo> getSlis() {
        return slis;
    }

    public void setSlis(List<OpenSlo> slis) {
        this.slis = slis;
    }

    public List<OpenSlo> getSlos() {
        return slos;
    }

    public void setSlos(List<OpenSlo> slos) {
        this.slos = slos;
    }

    public List<OpenSlo> getIndicatorSlis() {
        return indicatorSlis;
    }

    public void setIndicatorSlis(List<OpenSlo> indicatorSlis) {
        this.indicatorSlis = indicatorSlis;
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

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentValue) {
        this.currentValue = currentValue;
    }
}
