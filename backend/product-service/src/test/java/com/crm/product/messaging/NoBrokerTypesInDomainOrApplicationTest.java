package com.crm.product.messaging;

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
 * ADR-017 §4 as an executable rule for product-service — the service that actually HAS a
 * broker adapter, which makes this the version of the guard with something to say.
 *
 * <p>The rule is not "Kafka is banned": it is that transport types live in exactly one
 * nameable package, so a reader can find the boundary from the package tree. Here that
 * package is {@code com.crm.product.messaging.adapter} — and the second test below proves
 * the exemption is load-bearing rather than decorative. If someone later moves the binding
 * into the service layer, the first test fails; if someone deletes the adapter and quietly
 * relaxes the rule, the second one does.
 *
 * <p>The forbidden list includes {@code org/springframework/messaging/} and not only the
 * Kafka packages, because that is where the boundary actually falls in practice: the
 * functional binding is a {@code Consumer<Message<byte[]>>}, so Spring's messaging
 * abstraction — not a Kafka type — is what a leak out of the adapter would look like. A
 * guard that only banned {@code org.apache.kafka} would have watched the wrong door.
 *
 * <p>Scanning compiled classes rather than source: an import grep misses inline
 * fully-qualified references and types reached through generic signatures, both of which
 * a compiler resolves happily and both of which would break class loading without the
 * transport on the classpath. The constant pool lists what a class really depends on.
 */
class NoBrokerTypesInDomainOrApplicationTest {

    private static final List<String> FORBIDDEN = List.of(
            "org/apache/kafka/",
            "org/springframework/kafka/",
            "org/springframework/cloud/stream/",
            "org/springframework/messaging/");

    /** What the adapter is EXPECTED to reference — see {@link #theExemptionIsLoadBearing()}. */
    private static final String TRANSPORT_MARKER = "org/springframework/messaging/";

    /** The single package permitted to see the broker (ADR-017 §4). */
    private static final String ADAPTER_PACKAGE = "com/crm/product/messaging/adapter/";

    private static Path classesRoot() {
        return Paths.get("target", "classes", "com", "crm", "product");
    }

    static boolean compiledClassesExist() {
        return Files.isDirectory(classesRoot());
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("no product-service class outside the adapter package references Kafka or Spring Cloud Stream")
    void domainAndApplicationPackagesAreBrokerFree() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path classFile : classFiles()) {
            String internalPath = internalPath(classFile);
            if (internalPath.startsWith(ADAPTER_PACKAGE)) {
                continue;
            }
            String bytecode = readAsLatin1(classFile);
            for (String forbidden : FORBIDDEN) {
                if (bytecode.contains(forbidden)) {
                    offenders.add(internalPath + " -> " + forbidden);
                }
            }
        }

        assertThat(offenders)
                .as("Domain/application classes must not depend on broker types (ADR-017 §4). "
                        + "Move the dependency into " + ADAPTER_PACKAGE + ", or express it through "
                        + "the OutboxPublisher / MessageHandler ports.")
                .isEmpty();
    }

    @Test
    @EnabledIf("compiledClassesExist")
    @DisplayName("the adapter package really is where the messaging binding lives")
    void theExemptionIsLoadBearing() throws IOException {
        List<String> adapterClassesReferencingTransport = new ArrayList<>();

        for (Path classFile : classFiles()) {
            String internalPath = internalPath(classFile);
            if (internalPath.startsWith(ADAPTER_PACKAGE)
                    && readAsLatin1(classFile).contains(TRANSPORT_MARKER)) {
                adapterClassesReferencingTransport.add(internalPath);
            }
        }

        // Without this, deleting the adapter would make the guard above pass vacuously —
        // a green test proving only that nothing exists.
        assertThat(adapterClassesReferencingTransport)
                .as("the functional binding is expected to live in " + ADAPTER_PACKAGE)
                .isNotEmpty();
    }

    private static List<Path> classFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(classesRoot())) {
            return paths.filter(p -> p.toString().endsWith(".class")).toList();
        }
    }

    private static String internalPath(Path classFile) {
        return classesRoot().getParent().getParent().getParent()
                .relativize(classFile).toString().replace('\\', '/');
    }

    /** ISO-8859-1 maps every byte to one char, so a substring search over it is exact. */
    private static String readAsLatin1(Path classFile) throws IOException {
        return new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
    }
}
