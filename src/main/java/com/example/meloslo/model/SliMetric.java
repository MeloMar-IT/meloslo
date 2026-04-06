package com.example.meloslo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SliMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private Double metricValue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sli_id")
    @JsonIgnoreProperties({"slis", "slos", "service"})
    private OpenSlo sli;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "datasource_id")
    @JsonIgnoreProperties({"slis", "slos", "service"})
    private OpenSlo datasource;

    public SliMetric() {
    }

    public SliMetric(LocalDateTime timestamp, Double value, OpenSlo sli, OpenSlo datasource) {
        this.timestamp = timestamp;
        this.metricValue = value;
        this.sli = sli;
        this.datasource = datasource;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getValue() {
        return metricValue;
    }

    public void setValue(Double value) {
        this.metricValue = value;
    }

    public OpenSlo getSli() {
        return sli;
    }

    public void setSli(OpenSlo sli) {
        this.sli = sli;
    }

    public OpenSlo getDatasource() {
        return datasource;
    }

    public void setDatasource(OpenSlo datasource) {
        this.datasource = datasource;
    }
}
