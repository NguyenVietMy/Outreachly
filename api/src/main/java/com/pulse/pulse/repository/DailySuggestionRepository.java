package com.pulse.pulse.repository;

import com.pulse.pulse.entity.DailySuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailySuggestionRepository extends JpaRepository<DailySuggestion, UUID> {

    Optional<DailySuggestion> findByUserIdAndSuggestionDate(Long userId, LocalDate suggestionDate);
}
