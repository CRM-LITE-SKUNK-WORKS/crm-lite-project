package com.crm.product.common;

/**
 * Acting principal for audit columns. The JWT {@code sub} claim in normal request
 * flow (ADR-004/009); this constant only for genuinely unauthenticated technical
 * work (Flyway seeds attribute themselves in SQL).
 */
public final class AuditActor {

    private AuditActor() {
    }

    public static final String SYSTEM = "system";
}
