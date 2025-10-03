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
                return companyRepository.findAllGlobalWithFilters(
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null,
                        pageable);
            } else {
                return companyRepository.findAll(pageable);
            }
        } else {
            // Organization-specific companies
            if (hasFilters) {
                return companyRepository.findWithFiltersByOrgId(
                        orgId,
                        search != null ? search.trim() : null,
                        companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                        size != null && !size.trim().isEmpty() ? size.trim() : null,
                        headquartersCountry != null && !headquartersCountry.trim().isEmpty()
                                ? headquartersCountry.trim()
                                : null,
                        pageable);
            } else {
                return companyRepository.findByOrgId(orgId, pageable);
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
        Company company = Company.builder()
                .name(name)
                .domain(domain)
                .orgId(orgId)
                .build();

        return companyRepository.save(company);
    }

    public Company updateCompany(UUID id, String name, String domain) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Company not found with id: " + id));

        company.setName(name);
        company.setDomain(domain);

        return companyRepository.save(company);
    }

    public void deleteCompany(UUID id) {
        companyRepository.deleteById(id);
    }

    public Company saveCompany(Company company) {
        return companyRepository.save(company);
    }
}
