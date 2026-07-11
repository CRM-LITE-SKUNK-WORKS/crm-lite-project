package com.crm.mernis.verify;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deterministic stub behaviour (documented in docs/runbooks/local-development.md):
 * a syntactically valid 11-digit nationality id verifies UNLESS it is on the deny
 * list. The default deny list gives local testers a stable "rejected" fixture.
 */
@ConfigurationProperties(prefix = "mernis.stub")
public record MernisStubProperties(List<String> deniedIds) {

    public MernisStubProperties {
        if (deniedIds == null || deniedIds.isEmpty()) {
            deniedIds = List.of("99999999999");
        }
    }
}
