package com.example.meloslo;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSloFunctionalTest extends BaseFunctionalTest {

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
    void shouldCreateAndRetrieveOpenSlo() {
        OpenSlo record = new OpenSlo("openslo/v1", "SLO", "test-slo", "Test SLO", "{}");
        record.setDepartment("HR");
        
        ResponseEntity<OpenSlo> createResponse = userAuth().postForEntity("/api/v1/openslo", record, OpenSlo.class);
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long id = createResponse.getBody().getId();
        
        ResponseEntity<OpenSlo> getResponse = userAuth().getForEntity("/api/v1/openslo/" + id, OpenSlo.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getName()).isEqualTo("test-slo");
    }

    @Test
    void shouldUpdateOpenSlo() {
        OpenSlo record = openSloRepository.save(new OpenSlo("openslo/v1", "SLO", "test-slo", "Test SLO", "{}"));
        
        record.setDisplayName("Updated SLO");
        HttpEntity<OpenSlo> request = new HttpEntity<>(record);
        
        ResponseEntity<OpenSlo> updateResponse = userAuth().exchange("/api/v1/openslo/" + record.getId(), HttpMethod.PUT, request, OpenSlo.class);
        
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().getDisplayName()).isEqualTo("Updated SLO");
    }

    @Test
    void shouldDeleteOpenSlo() {
        OpenSlo record = openSloRepository.save(new OpenSlo("openslo/v1", "SLO", "test-slo", "Test SLO", "{}"));
        
        userAuth().delete("/api/v1/openslo/" + record.getId());
        
        ResponseEntity<OpenSlo> getResponse = userAuth().getForEntity("/api/v1/openslo/" + record.getId(), OpenSlo.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldListOpenSloRecords() {
        openSloRepository.save(new OpenSlo("openslo/v1", "SLO", "slo-1", "SLO 1", "{}"));
        openSloRepository.save(new OpenSlo("openslo/v1", "SLI", "sli-1", "SLI 1", "{}"));
        
        ResponseEntity<OpenSlo[]> response = userAuth().getForEntity("/api/v1/openslo", OpenSlo[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        
        ResponseEntity<OpenSlo[]> filteredResponse = userAuth().getForEntity("/api/v1/openslo?kind=SLI", OpenSlo[].class);
        assertThat(filteredResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(filteredResponse.getBody()).hasSize(1);
        assertThat(filteredResponse.getBody()[0].getKind()).isEqualTo("SLI");
    }
}
