package com.crm.order.messaging;

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
 * ADR-017 §4 as an executable rule: <b>no domain or application package may reference a
 * broker type.</b>
 *
 * <p>This scans the compiled {@code .class} files rather than the source. Two reasons.
 * Grepping imports misses a fully-qualified reference written inline, and it misses a
 * type reached through a lambda or a generic signature — both of which a compiler happily
 * resolves and both of which would make the class un-loadable without Kafka on the
 * classpath. The constant pool, by contrast, contains every type the class actually
 * depends on, which is the property the rule is really about.
 *
 * <p>The allowed exception is {@code com.crm.order.messaging.adapter}, which since ADR-018
 * holds the six functional bindings that receive the SALE saga's replies. The rule is not
 * "Kafka is banned": it is that Kafka lives in exactly one nameable place, so a reader can
 * find the broker boundary by looking at package names. The second test asserts the
 * exemption is <b>load-bearing</b> — if someone moves a binding into the service layer the
 * first test fails, and if someone deletes the adapters and quietly keeps the exemption,
 * the third one does.
 *
 * <p>Skipped when {@code target/classes} is absent (an IDE running tests without a Maven
 * compile), rather than passing vacuously — a guard test that silently checks nothing is
 * worse than no guard test.
 */
class NoBrokerTypesInDomainOrApplicationTest {

    /** Internal-form prefixes as they appear in a class file's constant pool. */
    private static final List<String> FORBIDDEN = List.of(
            "org/apache/kafka/",
            "org/springframework/kafka/",
            "org/springframework/cloud/stream/");

    /** The one package permitted to see the broker (ADR-017 §4). */
    private static final List<String> ADAPTER_PACKAGES = List.of(
            "com/crm/order/messaging/adapter/");

    private static Path classesRoot() {
        return Paths.get("target", "classes", "com", "crm", "order");
    }

    static boolean compiledClassesExist() {
        return Files.isDirectory(classesRoot());
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("no com.crm.order class outside an adapter package references Kafka or Spring Cloud Stream")
    void domainAndApplicationPackagesAreBrokerFree() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> classFiles = Files.walk(classesRoot())) {
            for (Path classFile : classFiles.filter(p -> p.toString().endsWith(".class")).toList()) {
                String internalPath = classesRoot().getParent().getParent().getParent()
                        .relativize(classFile).toString().replace('\\', '/');
                if (ADAPTER_PACKAGES.stream().anyMatch(internalPath::startsWith)) {
                    continue;
                }
                // ISO-8859-1 maps every byte to one char, so a byte-level substring search
                // is exact here. Decoding as UTF-8 would mangle the constant pool's
                // non-text bytes and could split a match across a replacement character.
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

    /**
     * The exemption must be earning its keep. If the adapter package stopped referencing
     * the transport, the rule above would still "pass" while protecting nothing — and the
     * next person to add a binding would have no reason to put it in the right place.
     */
    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("the adapter package really does reference the transport — the exemption is load-bearing")
    void adapterPackageActuallyUsesTheTransport() throws IOException {
        Path adapters = classesRoot().resolve(Paths.get("messaging", "adapter"));
        assertThat(Files.isDirectory(adapters))
                .as("ADR-018 puts the SALE saga's stream bindings in com.crm.order.messaging.adapter")
                .isTrue();

        boolean referencesTransport = false;
        try (Stream<Path> classFiles = Files.walk(adapters)) {
            for (Path classFile : classFiles.filter(p -> p.toString().endsWith(".class")).toList()) {
                String bytecode = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
                if (bytecode.contains("org/springframework/messaging/")
                        || FORBIDDEN.stream().anyMatch(bytecode::contains)) {
                    referencesTransport = true;
                    break;
                }
            }
        }
        assertThat(referencesTransport)
                .as("if the adapter package no longer touches the transport, the exemption above "
                        + "is protecting nothing and should be removed rather than left as decoration")
                .isTrue();
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("the guard actually scans something — a vacuous pass is a failed guard")
    void scanCoversTheServiceClasses() throws IOException {
        try (Stream<Path> classFiles = Files.walk(classesRoot())) {
            long scanned = classFiles.filter(p -> p.toString().endsWith(".class")).count();
            assertThat(scanned)
                    .as("order-service has far more than 20 compiled classes; a lower count means "
                            + "the scan root moved and the test above is checking nothing")
                    .isGreaterThan(20);
        }
    }
}
