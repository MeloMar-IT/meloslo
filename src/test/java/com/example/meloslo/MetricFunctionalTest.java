package com.example.meloslo;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.MetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricFunctionalTest extends BaseFunctionalTest {

    @Autowired
    private OpenSloRepository openSloRepository;

    @Autowired
    private MetricRepository metricRepository;

    @BeforeEach
    void setup() {
        metricRepository.deleteAll();
        openSloRepository.deleteAll();
    }

    @Test
    void shouldCreateAndListMetrics() {
        OpenSlo sli = openSloRepository.save(new OpenSlo("openslo/v1", "SLI", "test-sli", "Test SLI", "{}"));
        
        SliMetric metric = new SliMetric(LocalDateTime.now(), 99.9, sli, null);
        
        ResponseEntity<SliMetric> createResponse = adminAuth().postForEntity("/api/v1/metrics", metric, SliMetric.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        ResponseEntity<SliMetric[]> listResponse = adminAuth().getForEntity("/api/v1/metrics", SliMetric[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0].getValue()).isEqualTo(99.9);
    }

    @Test
    void shouldReturnForbiddenForRegularUser() {
        ResponseEntity<Object> response = userAuth().getForEntity("/api/v1/metrics", Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
