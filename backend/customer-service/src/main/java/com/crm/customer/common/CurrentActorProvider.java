package com.crm.customer.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Acting principal for audit columns (ADR-004/ADR-009): the Keycloak {@code sub} of
 * the current request's JWT, provided by crm-security-starter's AuditorAware; the
 * constant {@link AuditActor#SYSTEM} only for genuinely unauthenticated technical
 * work (Flyway migrations and seeds attribute themselves in SQL).
 */
@Component
@RequiredArgsConstructor
public class CurrentActorProvider {

    private final AuditorAware<String> auditorAware;

    public String get() {
        return auditorAware.getCurrentAuditor().orElse(AuditActor.SYSTEM);
    }
}
