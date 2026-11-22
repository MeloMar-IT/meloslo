package com.example.meloslo.repository;

import com.example.meloslo.model.SliMetric;
import com.example.meloslo.model.OpenSlo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<SliMetric, Long> {
    List<SliMetric> findBySliOrderByTimestampDesc(OpenSlo sli);
    List<SliMetric> findBySliAndTimestampAfterOrderByTimestampDesc(OpenSlo sli, LocalDateTime timestamp);
    
    @org.springframework.data.jpa.repository.Query("SELECT m FROM SliMetric m WHERE m.sli IN :slis AND m.timestamp > :timestamp ORDER BY m.timestamp DESC")
    List<SliMetric> findBySliInAndTimestampAfterOrderByTimestampDesc(java.util.Collection<OpenSlo> slis, LocalDateTime timestamp);
    
    /** One-time migration: legacy rows (no scale marker) with a fraction value become percent values. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE SliMetric m SET m.metricValue = m.metricValue * 100, m.valueScale = 'PERCENT' WHERE m.valueScale IS NULL AND m.metricValue <= 1.0")
    int convertLegacyFractionValues();

    /** Marks the remaining legacy rows (already on the 0 to 100 scale) so the migration never touches them again. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE SliMetric m SET m.valueScale = 'PERCENT' WHERE m.valueScale IS NULL")
    int markLegacyValuesAsPercent();

    long countBySli(OpenSlo sli);
    void deleteBySli(OpenSlo sli);
    void deleteByDatasource(OpenSlo datasource);
}
