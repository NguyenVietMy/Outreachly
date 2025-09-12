package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.EnrichmentCache;
import com.outreachly.outreachly.entity.EnrichmentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EnrichmentCacheRepository extends JpaRepository<EnrichmentCache, String> {
    Optional<EnrichmentCache> findByKeyHashAndProvider(String keyHash, EnrichmentJob.Provider provider);
}
