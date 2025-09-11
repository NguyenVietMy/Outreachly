package com.outreachly.outreachly.service;

import com.outreachly.outreachly.entity.Template;
import com.outreachly.outreachly.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;

    public List<Template> listTemplates(UUID orgId, Template.Platform platform) {
        if (platform != null) {
            return templateRepository.findByOrgIdAndPlatformOrderByCreatedAtDesc(orgId, platform);
        }
        return templateRepository.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    public Template getTemplate(UUID orgId, UUID id) {
        return templateRepository.findByIdAndOrgId(id, orgId).orElse(null);
    }

    @Transactional
    public Template createTemplate(UUID orgId, String name, Template.Platform platform, String category,
            String contentJson) {
        Template template = Template.builder()
                .orgId(orgId)
                .name(name)
                .platform(platform)
                .category(category)
                .contentJson(contentJson)
                .build();
        return templateRepository.save(template);
    }

    @Transactional
    public Template updateTemplate(UUID orgId, UUID id, String name, String category, String contentJson,
            Template.Platform platform) {
        Template existing = templateRepository.findByIdAndOrgId(id, orgId).orElse(null);
        if (existing == null)
            return null;
        if (name != null)
            existing.setName(name);
        if (category != null)
            existing.setCategory(category);
        if (contentJson != null)
            existing.setContentJson(contentJson);
        if (platform != null)
            existing.setPlatform(platform);
        return templateRepository.save(existing);
    }

    @Transactional
    public boolean deleteTemplate(UUID orgId, UUID id) {
        Template existing = templateRepository.findByIdAndOrgId(id, orgId).orElse(null);
        if (existing == null)
            return false;
        templateRepository.delete(existing);
        return true;
    }
}
