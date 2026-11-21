package com.example.meloslo.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagementServiceTest {

    @Test
    void shouldRunTaskInSeparateThread() throws InterruptedException {
        TaskManagementService service = new TaskManagementService();
        String mainThreadName = Thread.currentThread().getName();
        final String[] taskThreadName = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        service.runAsyncTask("test-task", () -> {
            taskThreadName[0] = Thread.currentThread().getName();
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotNull(taskThreadName[0]);
        assertNotEquals(mainThreadName, taskThreadName[0]);
    }

    @Test
    void shouldStopOldTaskIfRunningMoreThanFiveMinutes() throws InterruptedException {
        // Mock clock to control time
        final Instant[] now = {Instant.parse("2026-04-05T18:00:00Z")};
        Clock mockClock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };

        TaskManagementService service = new TaskManagementService(mockClock);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch finishedLatch = new CountDownLatch(1);

        service.runAsyncTask("long-task", () -> {
            startedLatch.countDown();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            } finally {
                finishedLatch.countDown();
            }
        });

        assertTrue(startedLatch.await(1, TimeUnit.SECONDS));
        
        // Advance time by 6 minutes
        now[0] = now[0].plus(java.time.Duration.ofMinutes(6));
        
        // Trigger cleanup manually
        service.cleanupOldTasks();
        
        // long-task should be interrupted
        assertTrue(finishedLatch.await(1, TimeUnit.SECONDS), "Task should have finished (interrupted)");
        assertTrue(interrupted.get(), "Task should have been interrupted");
    }
}
