package com.crm.security.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

class CrmResourceServerAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CrmResourceServerAutoConfiguration.class));

    @Test
    void withoutIssuerPropertyNoDecoderIsCreated() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JwtDecoder.class);
            // Support beans are still available (converter, interceptor, handlers, auditor).
            assertThat(context).hasSingleBean(BearerTokenPropagationInterceptor.class);
            assertThat(context).hasSingleBean(AuditorAware.class);
        });
    }

    @Test
    void withIssuerPropertyDecoderAndDefaultChainAreCreated() {
        runner.withUserConfiguration(WebSecurityInfrastructure.class)
                .withPropertyValues("crm.security.issuer=http://localhost:8180/realms/crm-lite")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context).hasBean("crmResourceServerSecurityFilterChain");
                    assertThat(context).hasSingleBean(RestAuthenticationEntryPoint.class);
                    assertThat(context).hasSingleBean(RestAccessDeniedHandler.class);
                });
    }

    @Test
    void serviceProvidedDecoderBacksOffTheDefault() {
        runner.withUserConfiguration(WebSecurityInfrastructure.class, CustomDecoderConfiguration.class)
                .withPropertyValues("crm.security.issuer=http://localhost:8180/realms/crm-lite")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasBean("customDecoder");
                });
    }

    /** Provides the HttpSecurity prototype the starter's chain bean needs (Boot does this in a real app). */
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityInfrastructure {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecoderConfiguration {

        @Bean
        JwtDecoder customDecoder() {
            return NimbusJwtDecoder.withJwkSetUri("http://example.invalid/jwks").build();
        }
    }
}
