package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.UserGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserGoalRepository extends JpaRepository<UserGoal, UUID> {

    List<UserGoal> findByUserId(Long userId);

    List<UserGoal> findByUserIdAndStatus(Long userId, String status);
}
