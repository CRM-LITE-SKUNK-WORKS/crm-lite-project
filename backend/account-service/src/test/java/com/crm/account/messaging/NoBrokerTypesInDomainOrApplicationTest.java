package com.crm.account.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * ADR-017 §4 as an executable rule for account-service.
 *
 * <p>account-service has no adapter package today — it records to its Outbox and consumes
 * nothing — so the rule here has no exemptions at all: not one compiled class may
 * reference a broker type. The day a saga step lands here, the binding goes in
 * {@code com.crm.account.messaging.adapter} and this list gains exactly one entry.
 *
 * <p>Scanning compiled classes rather than source, because an import grep misses inline
 * fully-qualified references and types reached through generic signatures.
 */
class NoBrokerTypesInDomainOrApplicationTest {

    private static final List<String> FORBIDDEN = List.of(
            "org/apache/kafka/",
            "org/springframework/kafka/",
            "org/springframework/cloud/stream/");

    /** No exemption today, and stated rather than left implicit. */
    private static final List<String> ADAPTER_PACKAGES = List.of("com/crm/account/messaging/adapter/");

    private static Path classesRoot() {
        return Paths.get("target", "classes", "com", "crm", "account");
    }

    static boolean compiledClassesExist() {
        return Files.isDirectory(classesRoot());
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("no com.crm.account class outside an adapter package references Kafka or Spring Cloud Stream")
    void domainAndApplicationPackagesAreBrokerFree() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(classesRoot())) {
            for (Path classFile : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String internalPath = classesRoot().getParent().getParent().getParent()
                        .relativize(classFile).toString().replace('\\', '/');
                if (ADAPTER_PACKAGES.stream().anyMatch(internalPath::startsWith)) {
                    continue;
                }
                // ISO-8859-1 maps every byte to one char, so this substring search is exact.
                String bytecode = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
                for (String forbidden : FORBIDDEN) {
                    if (bytecode.contains(forbidden)) {
                        offenders.add(internalPath + " -> " + forbidden);
                    }
                }
            }
        }

        assertThat(offenders)
                .as("Domain/application classes must not depend on broker types (ADR-017 §4). "
                        + "Move the dependency into a *.messaging.adapter package, or express it "
                        + "through the OutboxPublisher / MessageHandler ports.")
                .isEmpty();
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("the guard actually scans something — a vacuous pass is a failed guard")
    void scanCoversTheServiceClasses() throws IOException {
        try (Stream<Path> paths = Files.walk(classesRoot())) {
            assertThat(paths.filter(p -> p.toString().endsWith(".class")).count())
                    .as("account-service has far more than 20 compiled classes; a lower count means "
                            + "the scan root moved and the test above is checking nothing")
                    .isGreaterThan(20);
        }
    }
}
