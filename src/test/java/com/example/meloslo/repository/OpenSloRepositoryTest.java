package com.example.meloslo.repository;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class OpenSloRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OpenSloRepository repository;

    @Autowired
    private MetricRepository metricRepository;

    @Test
    void shouldFindByName() {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "UniqueName", "Display Name", "{}");
        entityManager.persist(slo);
        entityManager.flush();

        Optional<OpenSlo> found = repository.findByName("UniqueName");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("UniqueName");
    }

    @Test
    void shouldReturnEmptyForNonExistentName() {
        Optional<OpenSlo> found = repository.findByName("NonExistent");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldSaveOpenSlo() {
        OpenSlo slo = new OpenSlo("openslo/v1", "SLO", "NewSLO", "New SLO", "{}");
        OpenSlo saved = repository.save(slo);

        assertThat(saved.getId()).isNotNull();
        assertThat(entityManager.find(OpenSlo.class, saved.getId())).isNotNull();
    }

    @Test
    void shouldSaveMetric() {
        OpenSlo sli = new OpenSlo("openslo/v1", "SLI", "SliForMetric", "Sli For SliMetric", "{}");
        entityManager.persist(sli);
        entityManager.flush();

        SliMetric metric = new SliMetric(LocalDateTime.now(), 0.95, sli, null);
        SliMetric saved = metricRepository.save(metric);

        assertThat(saved.getId()).isNotNull();
        assertThat(entityManager.find(SliMetric.class, saved.getId())).isNotNull();
    }
}
