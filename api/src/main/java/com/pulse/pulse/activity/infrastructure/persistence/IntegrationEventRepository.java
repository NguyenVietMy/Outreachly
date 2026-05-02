package com.pulse.pulse.activity.infrastructure.persistence;

import com.pulse.pulse.activity.domain.IntegrationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, UUID> {

    List<IntegrationEvent> findByUserIdOrderByEventTimestampDesc(Long userId, Pageable pageable);

    @Query("SELECT ie.provider, COUNT(ie) FROM IntegrationEvent ie " +
           "WHERE ie.userId = :userId AND ie.eventTimestamp >= :since " +
           "GROUP BY ie.provider")
    List<Object[]> countByProviderSince(@Param("userId") Long userId,
                                        @Param("since") LocalDateTime since);

    boolean existsByUserIdAndProviderAndExternalId(Long userId, String provider, String externalId);

    List<IntegrationEvent> findByUserIdAndEventTimestampAfterOrderByEventTimestampDesc(
            Long userId, LocalDateTime since);

    @Query("SELECT CAST(ie.eventTimestamp AS date), COUNT(ie) FROM IntegrationEvent ie " +
           "WHERE ie.userId = :userId AND ie.provider = :provider " +
           "AND ie.eventTimestamp >= :since " +
           "GROUP BY CAST(ie.eventTimestamp AS date) " +
           "ORDER BY CAST(ie.eventTimestamp AS date)")
    List<Object[]> countByProviderPerDay(@Param("userId") Long userId,
                                          @Param("provider") String provider,
                                          @Param("since") LocalDateTime since);

    @Query("SELECT ie.provider, ie.eventType, COUNT(ie) FROM IntegrationEvent ie " +
           "WHERE ie.userId = :userId AND ie.eventTimestamp >= :since " +
           "GROUP BY ie.provider, ie.eventType")
    List<Object[]> countByProviderAndEventTypeSince(@Param("userId") Long userId,
                                                     @Param("since") LocalDateTime since);

    @Query("SELECT CAST(ie.eventTimestamp AS date), COUNT(ie) FROM IntegrationEvent ie " +
           "WHERE ie.userId = :userId AND ie.eventTimestamp >= :since " +
           "GROUP BY CAST(ie.eventTimestamp AS date) " +
           "ORDER BY CAST(ie.eventTimestamp AS date)")
    List<Object[]> countByUserIdGroupByDate(@Param("userId") Long userId,
                                             @Param("since") LocalDateTime since);
}
