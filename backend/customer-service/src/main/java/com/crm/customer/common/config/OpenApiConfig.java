package com.crm.customer.common.config;

import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Forces a relative "server" URL in the generated OpenAPI document (ADR-012).
 *
 * <p>Without this, springdoc self-detects the server URL from the request it actually
 * received — this container's own hostname:port, since the gateway proxy hop isn't
 * reliably reflected via X-Forwarded-* even with {@code server.forward-headers-strategy}.
 * A relative URL makes the gateway-hosted Swagger UI's "Try it out" resolve calls
 * against its own origin instead, with no dependency on proxy header propagation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer relativeServerCustomizer() {
        return openApi -> openApi.setServers(List.of(new Server().url("/")));
    }
}
