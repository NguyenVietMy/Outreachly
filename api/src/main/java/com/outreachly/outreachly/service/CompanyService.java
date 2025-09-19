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

    public Page<Company> getCompanies(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (search != null && !search.trim().isEmpty()) {
            log.info("Searching companies with query: '{}', page: {}, size: {}", search, page, size);
            return companyRepository.findWithFilters(search.trim(), pageable);
        } else {
            log.info("Fetching all companies, page: {}, size: {}", page, size);
            return companyRepository.findAll(pageable);
        }
    }

    public long getCompanyCount(String search) {
        if (search != null && !search.trim().isEmpty()) {
            return companyRepository.countWithFilters(search.trim());
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
}
