package com.pulse.pulse.service;

import com.pulse.pulse.entity.UserIntegration;
import com.pulse.pulse.repository.UserIntegrationRepository;
import com.pulse.pulse.service.integration.IntegrationProvider;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IntegrationSyncScheduler {

    private final UserIntegrationRepository integrationRepo;
    private final IntegrationService integrationService;
    private final Map<String, IntegrationProvider> providers;
    private final ExecutorService syncExecutor;

    @Value("${sync.auto.interval-minutes:10}")
    private int syncIntervalMinutes;

    @Value("${sync.auto.batch-size:50}")
    private int batchSize;

    @Value("${sync.auto.max-consecutive-failures:5}")
    private int maxConsecutiveFailures;

    @Value("${sync.auto.backoff-base-minutes:10}")
    private int backoffBaseMinutes;

    @Value("${sync.auto.backoff-max-minutes:1440}")
    private int backoffMaxMinutes;

    @Value("${sync.auto.provider-rate-limit-per-minute:30}")
    private int providerRateLimitPerMinute;

    @Value("${sync.auto.thread-pool-size:4}")
    private int threadPoolSize;

    @Value("${sync.auto.enabled:true}")
    private boolean autoSyncEnabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // --- Metrics ---
    private final AtomicLong totalSyncRuns = new AtomicLong(0);
    private final AtomicLong totalSyncsAttempted = new AtomicLong(0);
    private final AtomicLong totalSyncsSucceeded = new AtomicLong(0);
    private final AtomicLong totalSyncsFailed = new AtomicLong(0);
    private final AtomicLong totalEventsIngested = new AtomicLong(0);
    private final ConcurrentHashMap<String, ProviderMetrics> providerMetrics = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastRunStartedAt;
    private volatile LocalDateTime lastRunCompletedAt;
    private volatile long lastRunDurationMs;

    // --- Per-provider rate limiting ---
    private final ConcurrentHashMap<String, Semaphore> providerSemaphores = new ConcurrentHashMap<>();

    public IntegrationSyncScheduler(UserIntegrationRepository integrationRepo,
                                     IntegrationService integrationService,
                                     Map<String, IntegrationProvider> providers) {
        this.integrationRepo = integrationRepo;
        this.integrationService = integrationService;
        this.providers = providers;
        this.syncExecutor = Executors.newFixedThreadPool(4);
    }

    @Scheduled(fixedRate = 600000)
    public void autoSync() {
        if (!autoSyncEnabled) {
            log.debug("Auto-sync is disabled");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("Previous sync run still in progress, skipping");
            return;
        }

        long runStart = System.currentTimeMillis();
        lastRunStartedAt = LocalDateTime.now();
        totalSyncRuns.incrementAndGet();

        try {
            executeSyncCycle();
        } catch (Exception e) {
            log.error("Auto-sync cycle failed unexpectedly", e);
        } finally {
            lastRunDurationMs = System.currentTimeMillis() - runStart;
            lastRunCompletedAt = LocalDateTime.now();
            running.set(false);
            log.info("Auto-sync cycle completed in {}ms", lastRunDurationMs);
        }
    }

    private void executeSyncCycle() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleCutoff = now.minusMinutes(syncIntervalMinutes);

        List<UserIntegration> eligible = integrationRepo.findSyncEligible(staleCutoff, now);

        if (eligible.isEmpty()) {
            log.debug("No integrations eligible for auto-sync");
            return;
        }

        List<UserIntegration> batch = eligible.stream()
                .limit(batchSize)
                .collect(Collectors.toList());

        log.info("Auto-sync: {} eligible, processing batch of {}", eligible.size(), batch.size());

        Map<String, List<UserIntegration>> byProvider = batch.stream()
                .collect(Collectors.groupingBy(UserIntegration::getProvider));

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, List<UserIntegration>> entry : byProvider.entrySet()) {
            String provider = entry.getKey();
            List<UserIntegration> integrations = entry.getValue();

            if (!providers.containsKey(provider)) {
                log.warn("No provider implementation for '{}', skipping {} integrations", provider, integrations.size());
                continue;
            }

            Semaphore rateLimiter = providerSemaphores.computeIfAbsent(
                    provider, k -> new Semaphore(providerRateLimitPerMinute));

            for (UserIntegration integration : integrations) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        if (!rateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
                            log.warn("Rate limit hit for provider '{}', deferring userId={}", provider, integration.getUserId());
                            return;
                        }
                        syncSingleIntegration(integration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Sync interrupted for provider='{}', userId={}", provider, integration.getUserId());
                    }
                }, syncExecutor);

                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(5, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    log.error("Auto-sync batch timed out or failed", ex);
                    return null;
                })
                .join();

        providerSemaphores.values().forEach(s -> s.release(providerRateLimitPerMinute - s.availablePermits()));
    }

    private void syncSingleIntegration(UserIntegration integration) {
        String provider = integration.getProvider();
        Long userId = integration.getUserId();
        long startMs = System.currentTimeMillis();

        totalSyncsAttempted.incrementAndGet();
        getOrCreateProviderMetrics(provider).attempted.incrementAndGet();

        try {
            IntegrationService.SyncResult result = integrationService.sync(userId, provider);

            long durationMs = System.currentTimeMillis() - startMs;
            integration.setConsecutiveFailures(0);
            integration.setNextSyncAfter(null);
            integration.setLastSyncDurationMs(durationMs);
            integrationRepo.save(integration);

            totalSyncsSucceeded.incrementAndGet();
            totalEventsIngested.addAndGet(result.newEvents());
            ProviderMetrics pm = getOrCreateProviderMetrics(provider);
            pm.succeeded.incrementAndGet();
            pm.eventsIngested.addAndGet(result.newEvents());

            if (result.newEvents() > 0) {
                log.info("Auto-sync provider='{}' userId={}: {} new events ({}ms)",
                        provider, userId, result.newEvents(), durationMs);
            } else {
                log.debug("Auto-sync provider='{}' userId={}: no new events ({}ms)",
                        provider, userId, durationMs);
            }

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            totalSyncsFailed.incrementAndGet();
            getOrCreateProviderMetrics(provider).failed.incrementAndGet();

            int failures = (integration.getConsecutiveFailures() != null ? integration.getConsecutiveFailures() : 0) + 1;
            integration.setConsecutiveFailures(failures);
            integration.setLastSyncDurationMs(durationMs);

            if (failures >= maxConsecutiveFailures) {
                integration.setAutoSyncEnabled(false);
                integration.setNextSyncAfter(null);
                log.error("Auto-sync DISABLED for provider='{}' userId={} after {} consecutive failures. Last error: {}",
                        provider, userId, failures, e.getMessage());
            } else {
                long backoffMinutes = calculateBackoff(failures);
                integration.setNextSyncAfter(LocalDateTime.now().plusMinutes(backoffMinutes));
                log.warn("Auto-sync failed for provider='{}' userId={} (failure {}/{}), backing off {}min. Error: {}",
                        provider, userId, failures, maxConsecutiveFailures, backoffMinutes, e.getMessage());
            }

            integrationRepo.save(integration);
        }
    }

    private long calculateBackoff(int failures) {
        long backoff = (long) backoffBaseMinutes * (1L << (failures - 1));
        return Math.min(backoff, backoffMaxMinutes);
    }

    private ProviderMetrics getOrCreateProviderMetrics(String provider) {
        return providerMetrics.computeIfAbsent(provider, k -> new ProviderMetrics());
    }

    public SyncSchedulerStats getStats() {
        Map<String, ProviderStats> providerStatsMap = new HashMap<>();
        for (Map.Entry<String, ProviderMetrics> entry : providerMetrics.entrySet()) {
            ProviderMetrics pm = entry.getValue();
            providerStatsMap.put(entry.getKey(), ProviderStats.builder()
                    .attempted(pm.attempted.get())
                    .succeeded(pm.succeeded.get())
                    .failed(pm.failed.get())
                    .eventsIngested(pm.eventsIngested.get())
                    .build());
        }

        long activeAutoSync = integrationRepo.countByStatusAndAutoSyncEnabled("connected", true);

        return SyncSchedulerStats.builder()
                .enabled(autoSyncEnabled)
                .running(running.get())
                .syncIntervalMinutes(syncIntervalMinutes)
                .totalRuns(totalSyncRuns.get())
                .totalSyncsAttempted(totalSyncsAttempted.get())
                .totalSyncsSucceeded(totalSyncsSucceeded.get())
                .totalSyncsFailed(totalSyncsFailed.get())
                .totalEventsIngested(totalEventsIngested.get())
                .lastRunStartedAt(lastRunStartedAt)
                .lastRunCompletedAt(lastRunCompletedAt)
                .lastRunDurationMs(lastRunDurationMs)
                .activeAutoSyncIntegrations(activeAutoSync)
                .providerStats(providerStatsMap)
                .build();
    }

    public void triggerManualSync() {
        log.info("Manual auto-sync triggered");
        autoSync();
    }

    @Transactional
    public void resetBackoff(Long userId, String provider) {
        integrationRepo.findByUserIdAndProvider(userId, provider).ifPresent(integration -> {
            integration.setConsecutiveFailures(0);
            integration.setNextSyncAfter(null);
            integration.setAutoSyncEnabled(true);
            integrationRepo.save(integration);
            log.info("Reset backoff for provider='{}' userId={}", provider, userId);
        });
    }

    private static class ProviderMetrics {
        final AtomicLong attempted = new AtomicLong(0);
        final AtomicLong succeeded = new AtomicLong(0);
        final AtomicLong failed = new AtomicLong(0);
        final AtomicLong eventsIngested = new AtomicLong(0);
    }

    @Data
    @Builder
    public static class SyncSchedulerStats {
        private boolean enabled;
        private boolean running;
        private int syncIntervalMinutes;
        private long totalRuns;
        private long totalSyncsAttempted;
        private long totalSyncsSucceeded;
        private long totalSyncsFailed;
        private long totalEventsIngested;
        private LocalDateTime lastRunStartedAt;
        private LocalDateTime lastRunCompletedAt;
        private long lastRunDurationMs;
        private long activeAutoSyncIntegrations;
        private Map<String, ProviderStats> providerStats;
    }

    @Data
    @Builder
    public static class ProviderStats {
        private long attempted;
        private long succeeded;
        private long failed;
        private long eventsIngested;
    }
}
