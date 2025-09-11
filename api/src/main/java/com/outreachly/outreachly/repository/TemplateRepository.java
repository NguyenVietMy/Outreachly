package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID> {

    List<Template> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<Template> findByOrgIdAndPlatformOrderByCreatedAtDesc(UUID orgId, Template.Platform platform);

    Optional<Template> findByIdAndOrgId(UUID id, UUID orgId);
}

