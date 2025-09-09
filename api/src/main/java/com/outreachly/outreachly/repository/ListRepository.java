package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.LeadList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ListRepository extends JpaRepository<LeadList, UUID> {
    List<LeadList> findByOrgId(UUID orgId);
}
