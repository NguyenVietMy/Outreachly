package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.EmailEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailEventRepository extends JpaRepository<EmailEvent, Long> {

    List<EmailEvent> findByEmailAddressAndEventType(String emailAddress, EmailEvent.EmailEventType eventType);

    List<EmailEvent> findByProcessedFalse();

    @Query("SELECT e FROM EmailEvent e WHERE e.emailAddress = :emailAddress AND e.eventType IN :eventTypes")
    List<EmailEvent> findByEmailAddressAndEventTypeIn(@Param("emailAddress") String emailAddress,
            @Param("eventTypes") List<EmailEvent.EmailEventType> eventTypes);

    // RLS-compatible methods with org filtering
    @Query("SELECT e FROM EmailEvent e JOIN Lead l ON e.emailAddress = l.email WHERE l.orgId = :orgId AND e.processed = false")
    List<EmailEvent> findUnprocessedByOrgId(@Param("orgId") UUID orgId);

    @Query("SELECT e FROM EmailEvent e JOIN Lead l ON e.emailAddress = l.email WHERE l.orgId = :orgId AND e.emailAddress = :emailAddress AND e.eventType IN :eventTypes")
    List<EmailEvent> findByOrgIdAndEmailAddressAndEventTypeIn(@Param("orgId") UUID orgId,
            @Param("emailAddress") String emailAddress,
            @Param("eventTypes") List<EmailEvent.EmailEventType> eventTypes);

    @Query("SELECT COUNT(e) FROM EmailEvent e WHERE e.emailAddress = :emailAddress AND e.eventType = 'BOUNCE' AND e.timestamp >= :since")
    long countBouncesSince(@Param("emailAddress") String emailAddress, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(e) FROM EmailEvent e WHERE e.emailAddress = :emailAddress AND e.eventType = 'COMPLAINT' AND e.timestamp >= :since")
    long countComplaintsSince(@Param("emailAddress") String emailAddress, @Param("since") LocalDateTime since);
}
