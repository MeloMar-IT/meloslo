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

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldFindBySliOrderByTimestampDesc() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "test-sli", "Test SLI", "spec: {}");
        openSloRepository.save(sli);

        SliMetric m1 = new SliMetric(LocalDateTime.now().minusHours(2), 90.0, sli, null);
        SliMetric m2 = new SliMetric(LocalDateTime.now().minusHours(1), 95.0, sli, null);
        metricRepository.save(m1);
        metricRepository.save(m2);

        List<SliMetric> found = metricRepository.findBySliOrderByTimestampDesc(sli);

        assertEquals(2, found.size());
        assertEquals(95.0, found.get(0).getValue()); // m2 is newer
    }

    @Test
    void shouldMigrateLegacyFractionValuesOnceAndMarkTheRest() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "legacy-sli", "Legacy SLI", "spec: {}");
        openSloRepository.save(sli);
        LocalDateTime now = LocalDateTime.now();
        SliMetric a = metricRepository.save(new SliMetric(now, 0.95, sli, null));
        SliMetric b = metricRepository.save(new SliMetric(now, 1.0, sli, null));
        SliMetric c = metricRepository.save(new SliMetric(now, 0.0, sli, null));
        SliMetric d = metricRepository.save(new SliMetric(now, 99.5, sli, null));
        assertEquals("PERCENT", a.getValueScale());

        // Simulate rows written before the scale marker existed
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE sli_metric SET value_scale = NULL").executeUpdate();
        entityManager.clear();

        assertEquals(3, metricRepository.convertLegacyFractionValues());
        assertEquals(1, metricRepository.markLegacyValuesAsPercent());

        assertEquals(95.0, metricRepository.findById(a.getId()).orElseThrow().getValue(), 1e-9);
        assertEquals(100.0, metricRepository.findById(b.getId()).orElseThrow().getValue(), 1e-9);
        assertEquals(0.0, metricRepository.findById(c.getId()).orElseThrow().getValue(), 1e-9);
        assertEquals(99.5, metricRepository.findById(d.getId()).orElseThrow().getValue(), 1e-9);
        assertEquals("PERCENT", metricRepository.findById(d.getId()).orElseThrow().getValueScale());

        // A second run changes nothing
        assertEquals(0, metricRepository.convertLegacyFractionValues());
        assertEquals(0, metricRepository.markLegacyValuesAsPercent());
        assertEquals(95.0, metricRepository.findById(a.getId()).orElseThrow().getValue(), 1e-9);
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
