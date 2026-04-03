package com.example.meloslo.repository;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class MetricRepositoryTest {

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private OpenSloRepository openSloRepository;

    @Test
    void shouldFindBySliOrderByTimestampDesc() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "test-sli", "Test SLI", "spec: {}");
        openSloRepository.save(sli);

        SliMetric m1 = new SliMetric(LocalDateTime.now().minusHours(2), 0.9, sli, null);
        SliMetric m2 = new SliMetric(LocalDateTime.now().minusHours(1), 0.95, sli, null);
        metricRepository.save(m1);
        metricRepository.save(m2);

        List<SliMetric> found = metricRepository.findBySliOrderByTimestampDesc(sli);

        assertEquals(2, found.size());
        assertEquals(0.95, found.get(0).getValue()); // m2 is newer
    }

    @Test
    void shouldCountBySli() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "test-sli", "Test SLI", "spec: {}");
        openSloRepository.save(sli);

        metricRepository.save(new SliMetric(LocalDateTime.now(), 0.9, sli, null));

        long count = metricRepository.countBySli(sli);
        assertEquals(1, count);
    }

    @Test
    void shouldDeleteBySli() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "test-sli", "Test SLI", "spec: {}");
        openSloRepository.save(sli);

        metricRepository.save(new SliMetric(LocalDateTime.now(), 0.9, sli, null));
        
        metricRepository.deleteBySli(sli);
        
        assertEquals(0, metricRepository.countBySli(sli));
    }
}
