package com.blog.blog_literario.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.blog.blog_literario.config.properties.ResendProperties;
import com.resend.Resend;

import lombok.RequiredArgsConstructor;

/**
 * Provides the Resend SDK client used by
 * {@link com.blog.blog_literario.services.email.ResendEmailService}.
 *
 * <p>Only instantiated when {@code email.provider=resend}, so local dev and tests
 * (which default to the logging provider) never require a {@code RESEND_API_KEY}.
 */
@Configuration
@ConditionalOnProperty(name = "email.provider", havingValue = "resend")
@RequiredArgsConstructor
public class ResendConfig {

    private final ResendProperties resendProperties;

    @Bean
    public Resend resendClient() {
        return new Resend(resendProperties.apiKey());
    }
}
