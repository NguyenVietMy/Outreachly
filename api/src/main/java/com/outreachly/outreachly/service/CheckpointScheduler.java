package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.repository.CampaignCheckpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler service for processing campaign checkpoints.
 * Runs every 5 minutes to check for ready-to-execute checkpoints.
 * 
 * TODO: Upgrade to Redis/RabbitMQ for better scalability and reliability
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckpointScheduler {

    private final CampaignCheckpointRepository checkpointRepository;
    private final TimeService timeService;
    private final EmailDeliveryService emailDeliveryService;

    /**
     * Process all ready checkpoints every 5 minutes
     * Fixed rate: 300,000ms = 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void processReadyCheckpoints() {
        try {
            LocalDateTime now = timeService.nowUtc();
            LocalTime currentTime = now.toLocalTime();
            log.debug("Starting checkpoint processing at {}", now);

            // Find all active checkpoints that are ready to execute
            List<CampaignCheckpoint> readyCheckpoints = checkpointRepository
                    .findByStatusAndScheduledAtBefore(CampaignCheckpoint.CheckpointStatus.active, currentTime);

            if (readyCheckpoints.isEmpty()) {
                log.debug("No ready checkpoints found");
                return;
            }

            log.info("Processing {} ready checkpoints", readyCheckpoints.size());

            // Process each checkpoint
            int successCount = 0;
            int failureCount = 0;

            for (CampaignCheckpoint checkpoint : readyCheckpoints) {
                try {
                    log.info("Processing checkpoint: {} (ID: {})", checkpoint.getName(), checkpoint.getId());

                    // Send emails for this checkpoint
                    emailDeliveryService.sendCheckpointEmails(checkpoint);

                    // Mark checkpoint as completed
                    checkpoint.setStatus(CampaignCheckpoint.CheckpointStatus.completed);
                    checkpoint.setUpdatedAt(LocalDateTime.now());
                    checkpointRepository.save(checkpoint);

                    successCount++;
                    log.info("Successfully processed checkpoint: {}", checkpoint.getName());

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process checkpoint: {} (ID: {})",
                            checkpoint.getName(), checkpoint.getId(), e);

                    // Mark checkpoint as failed but don't stop processing others
                    checkpoint.setStatus(CampaignCheckpoint.CheckpointStatus.paused); // Pause failed checkpoints
                    checkpoint.setUpdatedAt(LocalDateTime.now());
                    checkpointRepository.save(checkpoint);
                }
            }

            log.info("Checkpoint processing completed. Success: {}, Failures: {}",
                    successCount, failureCount);

        } catch (Exception e) {
            log.error("Error in checkpoint scheduler", e);
        }
    }

    /**
     * Manual trigger for testing purposes
     */
    public void processCheckpointsNow() {
        log.info("Manual checkpoint processing triggered");
        processReadyCheckpoints();
    }

    /**
     * Get statistics about the scheduler
     */
    public SchedulerStats getSchedulerStats() {
        LocalDateTime now = timeService.nowUtc();

        long activeCheckpoints = checkpointRepository.countByStatus(CampaignCheckpoint.CheckpointStatus.active);
        long pendingCheckpoints = checkpointRepository.countByStatus(CampaignCheckpoint.CheckpointStatus.pending);
        long completedCheckpoints = checkpointRepository.countByStatus(CampaignCheckpoint.CheckpointStatus.completed);
        long pausedCheckpoints = checkpointRepository.countByStatus(CampaignCheckpoint.CheckpointStatus.paused);

        return SchedulerStats.builder()
                .lastRunTime(now)
                .activeCheckpoints(activeCheckpoints)
                .pendingCheckpoints(pendingCheckpoints)
                .completedCheckpoints(completedCheckpoints)
                .pausedCheckpoints(pausedCheckpoints)
                .totalCheckpoints(activeCheckpoints + pendingCheckpoints + completedCheckpoints + pausedCheckpoints)
                .build();
    }

    /**
     * Statistics for monitoring
     */
    @lombok.Data
    @lombok.Builder
    public static class SchedulerStats {
        private LocalDateTime lastRunTime;
        private long activeCheckpoints;
        private long pendingCheckpoints;
        private long completedCheckpoints;
        private long pausedCheckpoints;
        private long totalCheckpoints;
    }
}
