package com.example.meloslo.repository;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<SliMetric, Long> {
    List<SliMetric> findBySliOrderByTimestampDesc(OpenSlo sli);
    List<SliMetric> findBySliAndTimestampAfterOrderByTimestampDesc(OpenSlo sli, LocalDateTime timestamp);
    long countBySli(OpenSlo sli);
    void deleteBySli(OpenSlo sli);
    void deleteByDatasource(OpenSlo datasource);
}
