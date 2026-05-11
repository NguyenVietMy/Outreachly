package com.pulse.pulse.personal.infrastructure.persistence;

import com.pulse.pulse.personal.domain.RoadmapItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, UUID> {

    List<RoadmapItem> findByUserIdOrderByFocusRankAsc(Long userId);

    boolean existsByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
