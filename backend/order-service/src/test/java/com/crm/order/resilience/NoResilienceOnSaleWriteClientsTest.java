package com.crm.order.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.order.account.HttpAccountServiceClient;
import com.crm.order.product.HttpProductServiceClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Requirement 9 ("no retry on unsafe writes" / "do not add resilience policies to
 * order-service -> product-service or order-service -> account-service SALE
 * writes"): a compile-time-adjacent guarantee, not just documentation. Every
 * public method on the two SALE-write clients must carry NONE of
 * {@code @CircuitBreaker}/{@code @Bulkhead}/{@code @Retry} — including
 * {@code fetchAccount}, a GET that would otherwise be a plausible candidate,
 * deliberately excluded because this whole client is part of the SALE flow
 * scheduled to move to messaging (docs/runbooks/resilience.md).
 *
 * <p>If a future change adds one of these annotations here, this test fails
 * immediately instead of the drift being noticed only in a design review.
 */
class NoResilienceOnSaleWriteClientsTest {

    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation>[] RESILIENCE_ANNOTATIONS =
            new Class[] {CircuitBreaker.class, Bulkhead.class, Retry.class};

    @Test
    void httpAccountServiceClientCarriesNoResilience4jAnnotations() {
        assertNoResilienceAnnotations(HttpAccountServiceClient.class);
    }

    @Test
    void httpProductServiceClientCarriesNoResilience4jAnnotations() {
        assertNoResilienceAnnotations(HttpProductServiceClient.class);
    }

    private void assertNoResilienceAnnotations(Class<?> clientClass) {
        for (Method method : clientClass.getDeclaredMethods()) {
            for (Class<? extends Annotation> annotation : RESILIENCE_ANNOTATIONS) {
                assertThat(method.isAnnotationPresent(annotation))
                        .as("%s#%s must not carry @%s — it is part of the SALE-write boundary "
                                        + "excluded by requirement 9 (docs/runbooks/resilience.md)",
                                clientClass.getSimpleName(), method.getName(), annotation.getSimpleName())
                        .isFalse();
            }
        }
    }
}
