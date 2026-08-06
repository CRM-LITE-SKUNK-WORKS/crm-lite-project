package com.crm.observability.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Observability defaults for every CRM Lite deployable — including the ones with
 * no resource-server security at all (mernis-stub, discovery-server,
 * config-server), which is why this is a separate starter from
 * crm-security-starter rather than folded into it.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CrmObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Ahead of Spring Security's filter chain (registered around order -100 by
        // crm-security-starter / Boot's own auto-config) so the correlation id is
        // already in the MDC for a request security rejects before any controller runs.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdPropagationInterceptor correlationIdPropagationInterceptor() {
        return new CorrelationIdPropagationInterceptor();
    }
}
