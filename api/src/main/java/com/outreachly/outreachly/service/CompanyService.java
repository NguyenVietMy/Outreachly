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
            String size, String headquartersCountry, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);

        boolean hasFilters = (search != null && !search.trim().isEmpty()) ||
                (companyType != null && !companyType.trim().isEmpty()) ||
                (size != null && !size.trim().isEmpty()) ||
                (headquartersCountry != null && !headquartersCountry.trim().isEmpty());

        if (hasFilters) {
            log.info(
                    "Searching companies with filters: search='{}', type='{}', size='{}', country='{}', page: {}, size: {}",
                    search, companyType, size, headquartersCountry, page, pageSize);
            Page<Company> filteredCompanies = companyRepository.findWithFilters(
                    search != null ? search.trim() : null,
                    companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                    size != null && !size.trim().isEmpty() ? size.trim() : null,
                    headquartersCountry != null && !headquartersCountry.trim().isEmpty() ? headquartersCountry.trim()
                            : null,
                    pageable);
            log.info("Found {} companies with filters", filteredCompanies.getTotalElements());
            return filteredCompanies;
        } else {
            log.info("Fetching all companies, page: {}, size: {}", page, pageSize);
            Page<Company> allCompanies = companyRepository.findAll(pageable);
            log.info("Found {} companies in database", allCompanies.getTotalElements());
            return allCompanies;
        }
    }

    public long getCompanyCount(String search, String companyType,
            String size, String headquartersCountry) {
        boolean hasFilters = (search != null && !search.trim().isEmpty()) ||
                (companyType != null && !companyType.trim().isEmpty()) ||
                (size != null && !size.trim().isEmpty()) ||
                (headquartersCountry != null && !headquartersCountry.trim().isEmpty());

        if (hasFilters) {
            return companyRepository.countWithFilters(
                    search != null ? search.trim() : null,
                    companyType != null && !companyType.trim().isEmpty() ? companyType.trim() : null,
                    size != null && !size.trim().isEmpty() ? size.trim() : null,
                    headquartersCountry != null && !headquartersCountry.trim().isEmpty() ? headquartersCountry.trim()
                            : null);
        } else {
            return companyRepository.count();
        }
    }

    public Optional<Company> getCompanyById(UUID id) {
        return companyRepository.findById(id);
    }

    public Optional<Company> getCompanyByDomain(String domain) {
        return companyRepository.findByDomain(domain);
    }

    public Company createCompany(String name, String domain) {
        log.info("Creating company: name='{}', domain='{}'", name, domain);

        Company company = Company.builder()
                .name(name)
                .domain(domain)
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
