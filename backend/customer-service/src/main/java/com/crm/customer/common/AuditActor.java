package com.crm.customer.common;

/**
 * Acting principal for audit columns. Constant until authentication exists;
 * replaced by the Keycloak subject when ADR-004 is implemented.
 */
public final class AuditActor {

    private AuditActor() {
    }

    public static final String SYSTEM = "system";
}
