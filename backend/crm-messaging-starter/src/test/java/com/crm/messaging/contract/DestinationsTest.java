package com.crm.messaging.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The naming conventions (ADR-017 §7) pinned as tests.
 *
 * <p>Worth testing because these strings appear in three places that must agree without
 * being able to check each other at build time: the Java constants, the binding
 * configuration in config-repo, and the Debezium connector's routed destination values.
 * A silent rename in one of them produces a consumer that subscribes to a topic nobody
 * publishes to — which looks exactly like "no traffic yet".
 */
class DestinationsTest {

    @Test
    @DisplayName("event destinations follow crm.<domain>.evt.<fact>.v<n>")
    void eventNaming() {
        assertThat(Destinations.event(Destinations.DOMAIN_ORDER, "order-submitted", 1))
                .isEqualTo("crm.order.evt.order-submitted.v1");
    }

    @Test
    @DisplayName("command destinations use cmd, so a reader can tell the two apart at a glance")
    void commandNaming() {
        assertThat(Destinations.command(Destinations.DOMAIN_PRODUCT, "create-products", 1))
                .isEqualTo("crm.product.cmd.create-products.v1");
    }

    @Test
    @DisplayName("retry destinations are per attempt, not one shared channel")
    void retryNaming() {
        String destination = Destinations.event(Destinations.DOMAIN_ORDER, "order-submitted", 1);

        assertThat(Destinations.retry(destination, 1)).isEqualTo("crm.order.evt.order-submitted.v1.retry.1");
        assertThat(Destinations.retry(destination, 3)).isEqualTo("crm.order.evt.order-submitted.v1.retry.3");
    }

    @Test
    @DisplayName("the dead-letter destination is the source plus .dlq")
    void deadLetterNaming() {
        assertThat(Destinations.deadLetter("crm.order.evt.order-submitted.v1"))
                .isEqualTo("crm.order.evt.order-submitted.v1.dlq");
    }

    @Test
    @DisplayName("the consumer group names the READING service first")
    void consumerGroupNaming() {
        assertThat(Destinations.consumerGroup("product-service", "crm.order.evt.order-submitted.v1"))
                .isEqualTo("product-service.crm.order.evt.order-submitted.v1");
    }

    @Test
    @DisplayName("two services reading the same destination get two different groups")
    void groupsAreDistinctPerConsumer() {
        String destination = "crm.order.evt.order-submitted.v1";

        assertThat(Destinations.consumerGroup("product-service", destination))
                .isNotEqualTo(Destinations.consumerGroup("account-service", destination));
    }

    @Test
    @DisplayName("a version below 1 is a mistake, not a destination")
    void rejectsInvalidVersion() {
        assertThatThrownBy(() -> Destinations.event(Destinations.DOMAIN_ORDER, "order-submitted", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
