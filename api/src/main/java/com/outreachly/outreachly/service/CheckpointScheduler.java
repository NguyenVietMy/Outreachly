package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.CampaignCheckpoint;
import com.outreachly.outreachly.entity.CampaignCheckpointLead;
import com.outreachly.outreachly.entity.Campaign;
import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.repository.CampaignCheckpointRepository;
import com.outreachly.outreachly.repository.CampaignCheckpointLeadRepository;
import com.outreachly.outreachly.repository.CampaignRepository;
import com.outreachly.outreachly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final CampaignCheckpointLeadRepository checkpointLeadRepository;
    private final CampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final TimeService timeService;
    private final EmailDeliveryService emailDeliveryService;

    /**
     * Process all ready checkpoints every minute
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void processReadyCheckpoints() {
        try {
            LocalDateTime nowUtc = timeService.nowUtc();
            LocalDate today = nowUtc.toLocalDate();

            // Find all active checkpoints for today
            List<CampaignCheckpoint> activeCheckpoints = checkpointRepository
                    .findActiveCheckpointsForDate(today);

            if (activeCheckpoints.isEmpty()) {
                return;
            }

            log.info("Processing {} active checkpoints for {}", activeCheckpoints.size(), today);

            // Process each checkpoint with timezone awareness
            int successCount = 0;
            int failureCount = 0;

            for (CampaignCheckpoint checkpoint : activeCheckpoints) {
                try {
                    // Check if this checkpoint should execute now based on timezone
                    if (shouldExecuteCheckpoint(checkpoint, nowUtc)) {
                        log.info("Executing checkpoint: {} at {}", checkpoint.getName(), nowUtc);

                        // Send emails for this checkpoint
                        emailDeliveryService.sendCheckpointEmails(checkpoint);

                        // Determine checkpoint status based on results
                        updateCheckpointStatus(checkpoint);

                        successCount++;
                    }

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to process checkpoint: {} (ID: {})",
                            checkpoint.getName(), checkpoint.getId(), e);

                    // Mark checkpoint as paused on failure
                    checkpoint.setStatus(CampaignCheckpoint.CheckpointStatus.paused);
                    checkpoint.setUpdatedAt(LocalDateTime.now());
                    checkpointRepository.save(checkpoint);
                }
            }

            if (successCount > 0 || failureCount > 0) {
                log.info("Checkpoint processing completed. Success: {}, Failures: {}", successCount, failureCount);
            }

        } catch (Exception e) {
            log.error("Error in checkpoint scheduler", e);
        }
    }

    /**
     * Check if a checkpoint should execute now based on timezone
     */
    private boolean shouldExecuteCheckpoint(CampaignCheckpoint checkpoint, LocalDateTime nowUtc) {
        try {
            // Get campaign creator's timezone
            Campaign campaign = campaignRepository.findById(checkpoint.getCampaignId()).orElse(null);
            if (campaign == null) {
                log.warn("Campaign not found for checkpoint: {}", checkpoint.getId());
                return false;
            }

            User campaignCreator = userRepository.findById(campaign.getCreatedBy()).orElse(null);
            if (campaignCreator == null || campaignCreator.getTimezone() == null) {
                log.warn("Campaign creator or timezone not found for checkpoint: {}", checkpoint.getId());
                return false;
            }

            // Parse user's timezone and get current time in user's timezone
            LocalDateTime nowInUserTz = timeService.nowInTimezone(campaignCreator.getTimezone());

            // Convert checkpoint time to UTC
            LocalDateTime checkpointTimeInUserTz = nowInUserTz
                    .withHour(checkpoint.getTimeOfDay().getHour())
                    .withMinute(checkpoint.getTimeOfDay().getMinute())
                    .withSecond(0)
                    .withNano(0);

            // Convert to UTC for comparison
            ZoneOffset userTimezone = ZoneOffset.of(timeService.getTimezoneOffset(campaignCreator.getTimezone()));
            LocalDateTime checkpointTimeInUtc = checkpointTimeInUserTz.atOffset(userTimezone).toInstant()
                    .atOffset(ZoneOffset.UTC).toLocalDateTime();

            // Check if current UTC time is within 5 minutes of checkpoint time
            LocalDateTime fiveMinutesAgo = nowUtc.minusMinutes(5);
            LocalDateTime fiveMinutesFromNow = nowUtc.plusMinutes(5);

            boolean shouldExecute = checkpointTimeInUtc.isAfter(fiveMinutesAgo) &&
                    checkpointTimeInUtc.isBefore(fiveMinutesFromNow);

            if (shouldExecute) {
                log.debug("Checkpoint {} ready for execution. User time: {}, UTC time: {}",
                        checkpoint.getName(), checkpointTimeInUserTz, checkpointTimeInUtc);
            }

            return shouldExecute;

        } catch (Exception e) {
            log.error("Error checking checkpoint execution time for: {}", checkpoint.getId(), e);
            return false;
        }
    }

    /**
     * Update checkpoint status based on email delivery results
     */
    private void updateCheckpointStatus(CampaignCheckpoint checkpoint) {
        // Get delivery statistics for this checkpoint
        long sentLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpoint.getId(), CampaignCheckpointLead.DeliveryStatus.sent);
        long failedLeads = checkpointLeadRepository.countByCheckpointIdAndStatus(
                checkpoint.getId(), CampaignCheckpointLead.DeliveryStatus.failed);

        CampaignCheckpoint.CheckpointStatus newStatus;
        if (failedLeads == 0) {
            newStatus = CampaignCheckpoint.CheckpointStatus.completed;
        } else if (sentLeads > 0) {
            newStatus = CampaignCheckpoint.CheckpointStatus.partially_completed;
        } else {
            newStatus = CampaignCheckpoint.CheckpointStatus.paused; // All failed
        }

        checkpoint.setStatus(newStatus);
        checkpoint.setUpdatedAt(LocalDateTime.now());
        checkpointRepository.save(checkpoint);

        log.debug("Updated checkpoint {} status to {} ({} sent, {} failed)",
                checkpoint.getName(), newStatus, sentLeads, failedLeads);
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
