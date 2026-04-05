package com.example.meloslo.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void testUserEqualsAndHashCode() {
        User u1 = new User("admin", "pass", "admin@test.com", "Admin");
        u1.setId(1L);
        User u2 = new User("admin", "pass", "admin@test.com", "Admin");
        u2.setId(1L);
        User u3 = new User("other", "pass", "other@test.com", "Other");
        u3.setId(2L);

        assertEquals(u1, u2);
        assertNotEquals(u1, u3);
        assertEquals(u1.hashCode(), u2.hashCode());
        assertNotEquals(u1.hashCode(), u3.hashCode());
    }

    @Test
    void testOpenSloGettersAndSetters() {
        OpenSlo record = new OpenSlo("openslo/v1", "SLO", "Test", "Test", "{}");
        record.setId(1L);
        record.setDepartment("Finance");
        record.setManager("Jane Doe");
        record.setRefreshRate(15);
        record.setLastRefreshTime(LocalDateTime.now());

        assertEquals(1L, record.getId());
        assertEquals("Finance", record.getDepartment());
        assertEquals("Jane Doe", record.getManager());
        assertEquals(15, record.getRefreshRate());
        assertNotNull(record.getLastRefreshTime());
    }

    @Test
    void testSliMetricProperties() {
        LocalDateTime now = LocalDateTime.now();
        SliMetric metric = new SliMetric(now, 0.95, null, null);
        metric.setId(10L);

        assertEquals(now, metric.getTimestamp());
        assertEquals(0.95, metric.getValue());
        assertEquals(10L, metric.getId());
    }
}
