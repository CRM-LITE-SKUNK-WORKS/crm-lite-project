package com.crm.security.starter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource-server security defaults for CRM Lite domain services (ADR-009).
 *
 * <p>Activated by setting {@code crm.security.issuer}. Provides: a JwtDecoder
 * validating signature (JWKS), issuer (canonical, exact) and audience; Keycloak
 * realm-role mapping; a stateless bearer-only default filter chain requiring the
 * configured realm role on every endpoint except {@code crm.security.permit-paths};
 * consistent 401/403 JSON bodies; a JWT-sub {@link AuditorAware}; and an opt-in
 * outbound bearer-token propagation interceptor.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean}: a service that needs different
 * rules declares its own bean of the same type and the default backs off. Runs before
 * Boot's own security auto-configurations so the default chain here wins over Boot's
 * permit-nothing fallback chain, never the other way around.
 *
 * <p>Deliberate non-goals (ADR-009): no cookie/session handling (browser concerns
 * live only at the gateway BFF), no business authorization, no domain types.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JwtDecoder.class, HttpSecurity.class})
@EnableConfigurationProperties(CrmSecurityProperties.class)
public class CrmResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "crm.security", name = "issuer")
    public JwtDecoder crmJwtDecoder(CrmSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.resolveJwkSetUri()).build();
        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                defaultValidators, new AudienceValidator(properties.getAudience())));
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationConverter crmJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint crmAuthenticationEntryPoint() {
        return new RestAuthenticationEntryPoint();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler crmAccessDeniedHandler() {
        return new RestAccessDeniedHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "crm.security.service-account", name = "client-id")
    public ServiceAccountTokenProvider serviceAccountTokenProvider(CrmSecurityProperties properties) {
        CrmSecurityProperties.ServiceAccount config = properties.getServiceAccount();
        return new ServiceAccountTokenProvider(properties.resolveServiceAccountTokenUri(),
                config.getClientId(), config.getClientSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    public BearerTokenPropagationInterceptor bearerTokenPropagationInterceptor(
            ObjectProvider<ServiceAccountTokenProvider> serviceAccountTokenProvider) {
        return new BearerTokenPropagationInterceptor(serviceAccountTokenProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    @ConditionalOnBean(JwtDecoder.class)
    public SecurityFilterChain crmResourceServerSecurityFilterChain(HttpSecurity http,
                                                                    CrmSecurityProperties properties,
                                                                    JwtDecoder jwtDecoder,
                                                                    JwtAuthenticationConverter jwtAuthenticationConverter,
                                                                    RestAuthenticationEntryPoint entryPoint,
                                                                    RestAccessDeniedHandler deniedHandler) throws Exception {
        http
                // Bearer-only stateless API: no session, no cookies, hence no CSRF surface.
                // Browser cookie/CSRF handling exists only at the gateway BFF (ADR-007/008).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(properties.getPermitPaths().toArray(String[]::new)).permitAll()
                        // Explicit role, never bare authenticated() (binding direction #10).
                        .anyRequest().hasAuthority("ROLE_" + properties.getRequiredRole()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /**
     * AuditorAware only when Spring Data is on the classpath (optional dependency).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuditorAware.class)
    public static class CrmAuditorAwareConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        public AuditorAware<String> jwtAuditorAware() {
            return new JwtAuditorAware();
        }
    }

}
