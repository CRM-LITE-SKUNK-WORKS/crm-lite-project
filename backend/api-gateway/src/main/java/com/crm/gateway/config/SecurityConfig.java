package com.crm.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Servlet-based security for the WebMVC gateway.
 *
 * NOTE: This gateway uses spring-cloud-starter-gateway-server-webmvc (servlet stack),
 * so security is configured with the servlet {@link SecurityFilterChain} + {@link HttpSecurity},
 * NOT the reactive SecurityWebFilterChain / ServerHttpSecurity used by the WebFlux gateway.
 *
 * For now every request is permitted so routing can be exercised end-to-end. Once the
 * auth-service issues JWTs, tighten this to validate the token and authenticate requests.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API gateway: no browser session/forms, so CSRF is not applicable.
                // Left enabled it would block POST/PUT/DELETE requests.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}