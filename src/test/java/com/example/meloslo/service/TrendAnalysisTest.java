package com.example.meloslo.service;

import com.example.meloslo.model.OpenSlo;
import com.example.meloslo.model.SliMetric;
import com.example.meloslo.repository.MetricRepository;
import com.example.meloslo.repository.OpenSloRepository;
import com.example.meloslo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
public class TrendAnalysisTest {

    @Mock
    private OpenSloRepository repository;
    @Mock
    private MetricRepository metricRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OpenSloService service;

    private double target = 0.95;

    @SuppressWarnings("unchecked")
    private List<Double> invokeCalculateTrendPoints(List<SliMetric> metrics, double target, int futureDays) throws Exception {
        java.lang.reflect.Method method = OpenSloService.class.getDeclaredMethod("calculateTrendPoints", List.class, double.class, int.class);
        method.setAccessible(true);
        return (List<Double>) method.invoke(service, metrics, target, futureDays);
    }

    @Test
    void testTrendWithLinearData() throws Exception {
        List<SliMetric> metrics = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        // Create 30 days of data with a clear downward trend in error budget
        // Day 0: SLI = 0.99 (EB = (0.99-0.95)/(1-0.95)*100 = 80%)
        // Day 29: SLI = 0.96 (EB = (0.96-0.95)/(1-0.95)*100 = 20%)
        for (int i = 0; i < 30; i++) {
            double value = 0.99 - (0.03 * i / 29.0);
            metrics.add(new SliMetric(now.minusDays(29 - i), value, null, null));
        }

        List<Double> trend = invokeCalculateTrendPoints(metrics, 0.95, 30);
        
        assertThat(trend).hasSize(30);
        // Trend should continue downwards
        assertThat(trend.get(0)).isLessThan(20.0);
        assertThat(trend.get(29)).isLessThan(trend.get(0));
    }

    @Test
    void testTrendWithWeeklySeasonality() throws Exception {
        List<SliMetric> metrics = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        // Target 0.95 -> 1-target = 0.05
        // We want a constant trend (EB = 50%) but with weekly seasonality
        // Base value = 0.975 (EB = (0.975-0.95)/0.05 * 100 = 50%)
        for (int i = 0; i < 30; i++) {
            LocalDateTime ts = now.minusDays(29 - i);
            double seasonality = (ts.getDayOfWeek().getValue() >= 6) ? -0.01 : 0.005; // lower on weekends
            double value = 0.97 + seasonality; 
            metrics.add(new SliMetric(ts, value, null, null));
        }

        List<Double> trend = invokeCalculateTrendPoints(metrics, 0.95, 30);
        
        System.out.println("[DEBUG_LOG] Prophet-inspired Trend Projection: " + trend);

        assertThat(trend).hasSize(30);
        // The projection should show seasonality. 
        // Some values should be higher, some lower, matching the weekly cycle.
        double firstValue = trend.get(0);
        boolean foundDifferent = false;
        for (int i = 1; i < 7; i++) {
            if (Math.abs(trend.get(i) - firstValue) > 0.1) {
                foundDifferent = true;
                break;
            }
        }
        assertThat(foundDifferent).isTrue().as("Trend should show seasonality variations");
        
        // Check if day 0 and day 7 are similar (weekly seasonality)
        assertThat(trend.get(0)).isCloseTo(trend.get(7), within(1.0));
    }
}
