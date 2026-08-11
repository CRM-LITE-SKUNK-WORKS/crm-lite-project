package com.crm.order.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the two JSON columns on {@code sale_saga}.
 *
 * <p>A separate bean rather than inline {@code ObjectMapper} calls in the orchestrator,
 * for one reason worth stating: these two columns are the saga's memory across a restart,
 * and a serialization failure in the middle of a transition would surface as a checked
 * exception in the one place that must stay readable as a state machine. Here it is
 * converted once, at the boundary, into an {@link IllegalStateException} that is what it
 * actually is — corrupt state this service wrote itself.
 *
 * <p>It uses the application's {@code ObjectMapper}, not the wire codec's. These columns
 * are order-service's own storage, never a published contract, so the byte-stability rule
 * that governs {@code EnvelopeCodec} (ADR-017 §5.4) does not apply to them.
 */
@Component
@RequiredArgsConstructor
public class SaleSagaCodec {

    private static final TypeReference<List<Long>> PRODUCT_IDS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public String write(SaleRequestSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the submitted basket snapshot", e);
        }
    }

    public SaleRequestSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, SaleRequestSnapshot.class);
        } catch (Exception e) {
            // Written by this service and never hand-edited: unreachable outside data
            // corruption, which this method cannot repair and must not hide.
            throw new IllegalStateException("Corrupt sale saga request snapshot", e);
        }
    }

    public String writeProductIds(List<Long> productIds) {
        try {
            return objectMapper.writeValueAsString(productIds == null ? List.of() : productIds);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the saga's product ids", e);
        }
    }

    /** Empty rather than null for a saga that has not created products yet. */
    public List<Long> readProductIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, PRODUCT_IDS);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt sale saga product id list", e);
        }
    }
}
