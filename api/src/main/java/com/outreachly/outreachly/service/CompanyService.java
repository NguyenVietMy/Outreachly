package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Company;
import com.outreachly.outreachly.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;

    public Page<Company> getCompanies(String search, String companyType,
            String size, String headquartersCountry, int page, int pageSize, UUID orgId) {
        Pageable pageable = PageRequest.of(page, pageSize);

        boolean hasFilters = (search != null && !search.trim().isEmpty()) ||
                (companyType != null && !companyType.trim().isEmpty()) ||
                (size != null && !size.trim().isEmpty()) ||
                (headquartersCountry != null && !headquartersCountry.trim().isEmpty());

        if (orgId == null) {
            // Global companies (org_id IS NULL)
            if (hasFilters) {
                log.info(
                        "Searching global companies with filters: search='{}', type='{}', size='{}', country='{}', page: {}, size: {}",
                        search, companyType, size, headquartersCountry, page, pageSize);
                Page<Company> filteredCompanies = companyRepository.findAllGlobalWithFilters(
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null,
                        pageable);
                log.info("Found {} global companies with filters", filteredCompanies.getTotalElements());
                return filteredCompanies;
            } else {
                log.info("Fetching all global companies, page: {}, size: {}", page, pageSize);
                Page<Company> allCompanies = companyRepository.findAll(pageable);
                log.info("Found {} global companies in database", allCompanies.getTotalElements());
                return allCompanies;
            }
        } else {
            // Organization-specific companies
            if (hasFilters) {
                log.info(
                        "Searching companies with filters: search='{}', type='{}', size='{}', country='{}', page: {}, size: {}, orgId: {}",
                        search, companyType, size, headquartersCountry, page, pageSize, orgId);
                Page<Company> filteredCompanies = companyRepository.findWithFiltersByOrgId(
                        orgId,
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null,
                        pageable);
                log.info("Found {} companies with filters for org {}", filteredCompanies.getTotalElements(), orgId);
                return filteredCompanies;
            } else {
                log.info("Fetching all companies for org {}, page: {}, size: {}", orgId, page, pageSize);
                Page<Company> allCompanies = companyRepository.findByOrgId(orgId, pageable);
                log.info("Found {} companies in database for org {}", allCompanies.getTotalElements(), orgId);
                return allCompanies;
            }
        }
    }

    public long getCompanyCount(String search, String companyType,
            String size, String headquartersCountry, UUID orgId) {
        boolean hasFilters = (search != null && !search.trim().isEmpty()) ||
                (companyType != null && !companyType.trim().isEmpty()) ||
                (size != null && !size.trim().isEmpty()) ||
                (headquartersCountry != null && !headquartersCountry.trim().isEmpty());

        if (orgId == null) {
            // Global companies (org_id IS NULL)
            if (hasFilters) {
                return companyRepository.countAllGlobalWithFilters(
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null);
            } else {
                return companyRepository.count();
            }
        } else {
            // Organization-specific companies
            if (hasFilters) {
                return companyRepository.countWithFiltersByOrgId(
                        orgId,
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null);
            } else {
                return companyRepository.findByOrgId(orgId).size();
            }
        }
    }

    public Optional<Company> getCompanyById(UUID id) {
        return companyRepository.findById(id);
    }

    public Optional<Company> getCompanyByDomain(String domain) {
        return companyRepository.findByDomain(domain);
    }

    public Company createCompany(String name, String domain, UUID orgId) {
        log.info("Creating company: name='{}', domain='{}', orgId='{}'", name, domain, orgId);

        Company company = Company.builder()
                .name(name)
                .domain(domain)
                .orgId(orgId)
                .build();

        return companyRepository.save(company);
    }

    public Company updateCompany(UUID id, String name, String domain) {
        log.info("Updating company: id='{}', name='{}', domain='{}'", id, name, domain);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + id));

        company.setName(name);
        company.setDomain(domain);

        return companyRepository.save(company);
    }

    public void deleteCompany(UUID id) {
        log.info("Deleting company: id='{}'", id);
        companyRepository.deleteById(id);
    }

    public Company saveCompany(Company company) {
        log.info("Saving company: id='{}', name='{}', domain='{}'", company.getId(), company.getName(),
                company.getDomain());
        return companyRepository.save(company);
    }
}
