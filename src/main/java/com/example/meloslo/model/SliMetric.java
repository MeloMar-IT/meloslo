package com.example.meloslo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
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

    /** Value of {@code valueScale} for values on the 0 to 100 scale. */
    public static final String SCALE_PERCENT = "PERCENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private Double metricValue;

    /** Marks the scale of the stored value; NULL means a legacy row (fraction scale) awaiting migration. */
    @Column(name = "value_scale")
    private String valueScale = SCALE_PERCENT;

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

    @JsonIgnore
    public String getValueScale() {
        return valueScale;
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
