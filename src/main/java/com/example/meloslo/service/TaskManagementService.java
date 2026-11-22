package com.example.meloslo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class TaskManagementService {
    private static final Logger log = LoggerFactory.getLogger(TaskManagementService.class);
    private static final long MAX_TASK_DURATION_MINUTES = 5;

    private final Map<String, TaskInfo> runningTasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Clock clock;

    public TaskManagementService() {
        this(Clock.systemDefaultZone());
    }

    public TaskManagementService(Clock clock) {
        this.clock = clock;
    }

    public void runAsyncTask(String taskId, Runnable task) {
        // 1. Start new thread
        log.info("Starting async task for: {}", taskId);
        Future<?> future = executorService.submit(() -> {
            try {
                task.run();
            } finally {
                runningTasks.remove(taskId);
                log.debug("Async task for {} completed and removed from tracking.", taskId);
            }
        });

        runningTasks.put(taskId, new TaskInfo(future, LocalDateTime.now(clock)));
    }

    @Scheduled(fixedRate = 60000) // Cleanup every minute instead of on every task submission
    public void cleanupOldTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        runningTasks.entrySet().removeIf(entry -> {
            TaskInfo info = entry.getValue();
            if (Duration.between(info.startTime, now).toMinutes() >= MAX_TASK_DURATION_MINUTES) {
                log.warn("Task {} has been running for more than {} minutes. Stopping it.", entry.getKey(), MAX_TASK_DURATION_MINUTES);
                info.future.cancel(true);
                return true;
            }
            return false;
        });
    }

    private static class TaskInfo {
        final Future<?> future;
        final LocalDateTime startTime;

        TaskInfo(Future<?> future, LocalDateTime startTime) {
            this.future = future;
            this.startTime = startTime;
        }
    }
}
