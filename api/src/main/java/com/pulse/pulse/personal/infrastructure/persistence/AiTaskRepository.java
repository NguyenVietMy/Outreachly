package com.pulse.pulse.personal.infrastructure.persistence;

import com.pulse.pulse.personal.domain.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    List<AiTask> findByUserIdOrderByAxisAscOrderIndexAsc(Long userId);

    List<AiTask> findByUserIdAndAxisOrderByOrderIndexAsc(Long userId, String axis);

    @Transactional
    void deleteByUserIdAndAxisAndSectionId(Long userId, String axis, String sectionId);

    @Transactional
    void deleteByUserIdAndSource(Long userId, String source);
}
