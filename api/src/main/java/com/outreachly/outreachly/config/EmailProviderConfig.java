package com.outreachly.outreachly.config;

import com.outreachly.outreachly.service.email.EmailProvider;
import com.outreachly.outreachly.service.email.EmailProviderFactory;
import com.outreachly.outreachly.service.email.EmailProviderType;
import com.outreachly.outreachly.service.email.ResendEmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for email providers
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class EmailProviderConfig {

    /**
     * WebClient for Resend API calls
     */
    @Bean
    public WebClient resendWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
    }

    /**
     * Email provider factory with all available providers
     */
    @Bean
    public EmailProviderFactory emailProviderFactory(
            Map<String, EmailProvider> emailProviders) {

        Map<EmailProviderType, EmailProvider> providerMap = new HashMap<>();

        // Map Spring beans to provider types
        for (EmailProvider provider : emailProviders.values()) {
            providerMap.put(provider.getProviderType(), provider);
            log.info("Registered email provider: {}", provider.getProviderType().getDisplayName());
        }

        return new EmailProviderFactory(providerMap);
    }
}
