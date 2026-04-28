package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.AiTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiTaskRepository extends JpaRepository<AiTask, UUID> {

    List<AiTask> findByUserIdOrderByAxisAscOrderIndexAsc(Long userId);

    List<AiTask> findByUserIdAndAxisOrderByOrderIndexAsc(Long userId, String axis);

    void deleteByUserIdAndAxisAndSectionId(Long userId, String axis, String sectionId);

    void deleteByUserIdAndSource(Long userId, String source);
}
