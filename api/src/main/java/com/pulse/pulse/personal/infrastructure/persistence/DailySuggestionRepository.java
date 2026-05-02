package com.pulse.pulse.personal.infrastructure.persistence;

import com.pulse.pulse.personal.domain.DailySuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailySuggestionRepository extends JpaRepository<DailySuggestion, UUID> {

    Optional<DailySuggestion> findByUserIdAndSuggestionDate(Long userId, LocalDate suggestionDate);
}
