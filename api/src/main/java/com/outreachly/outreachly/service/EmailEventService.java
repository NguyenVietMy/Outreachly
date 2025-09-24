package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.EmailEvent;
import com.outreachly.outreachly.repository.EmailEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailEventService {

    private final EmailEventRepository emailEventRepository;

    @Transactional
    public EmailEvent saveEmailEvent(EmailEvent emailEvent) {
        return emailEventRepository.save(emailEvent);
    }

    @Transactional
    public void markAsProcessed(Long eventId) {
        emailEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessed(true);
            emailEventRepository.save(event);
        });
    }

    @Transactional
    public void markAsProcessed(List<Long> eventIds) {
        List<EmailEvent> events = emailEventRepository.findAllById(eventIds);
        events.forEach(event -> event.setProcessed(true));
        emailEventRepository.saveAll(events);
    }

    public List<EmailEvent> getUnprocessedEvents() {
        return emailEventRepository.findByProcessedFalse();
    }

    public List<EmailEvent> getBouncesForEmail(String emailAddress) {
        return emailEventRepository.findByEmailAddressAndEventType(emailAddress, EmailEvent.EmailEventType.BOUNCE);
    }

    public List<EmailEvent> getComplaintsForEmail(String emailAddress) {
        return emailEventRepository.findByEmailAddressAndEventType(emailAddress, EmailEvent.EmailEventType.COMPLAINT);
    }

    public long getBounceCountSince(String emailAddress, LocalDateTime since) {
        return emailEventRepository.countBouncesSince(emailAddress, since);
    }

    public long getComplaintCountSince(String emailAddress, LocalDateTime since) {
        return emailEventRepository.countComplaintsSince(emailAddress, since);
    }

    public boolean isEmailSuppressed(String emailAddress) {
        // Check for recent bounces or complaints
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long bounceCount = getBounceCountSince(emailAddress, thirtyDaysAgo);
        long complaintCount = getComplaintCountSince(emailAddress, thirtyDaysAgo);

        // Suppress if there are any complaints or more than 3 bounces in 30 days
        return complaintCount > 0 || bounceCount > 3;
    }

    public List<EmailEvent> getEmailHistory(String emailAddress) {
        return emailEventRepository.findByEmailAddressAndEventTypeIn(
                emailAddress,
                List.of(
                        EmailEvent.EmailEventType.BOUNCE,
                        EmailEvent.EmailEventType.COMPLAINT,
                        EmailEvent.EmailEventType.DELIVERY));
    }
}
