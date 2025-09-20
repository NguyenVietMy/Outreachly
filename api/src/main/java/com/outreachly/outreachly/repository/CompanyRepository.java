package com.outreachly.outreachly.repository;

import com.outreachly.outreachly.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

        Optional<Company> findByDomain(String domain);

        @Query(value = "SELECT * FROM companies c WHERE " +
                        "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
                        "(:companyType IS NULL OR c.company_type = :companyType) AND " +
                        "(:size IS NULL OR c.size = :size) AND " +
                        "(:headquartersCountry IS NULL OR c.headquarters_country = :headquartersCountry)", countQuery = "SELECT COUNT(*) FROM companies c WHERE "
                                        +
                                        "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND "
                                        +
                                        "(:companyType IS NULL OR c.company_type = :companyType) AND " +
                                        "(:size IS NULL OR c.size = :size) AND " +
                                        "(:headquartersCountry IS NULL OR c.headquarters_country = :headquartersCountry)", nativeQuery = true)
        Page<Company> findWithFilters(
                        @Param("search") String search,
                        @Param("companyType") String companyType,
                        @Param("size") String size,
                        @Param("headquartersCountry") String headquartersCountry,
                        Pageable pageable);

        @Query(value = "SELECT COUNT(*) FROM companies c WHERE " +
                        "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
                        "(:companyType IS NULL OR c.company_type = :companyType) AND " +
                        "(:size IS NULL OR c.size = :size) AND " +
                        "(:headquartersCountry IS NULL OR c.headquarters_country = :headquartersCountry)", nativeQuery = true)
        Long countWithFilters(
                        @Param("search") String search,
                        @Param("companyType") String companyType,
                        @Param("size") String size,
                        @Param("headquartersCountry") String headquartersCountry);
}
