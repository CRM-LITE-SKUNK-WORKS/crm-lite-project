package com.crm.product.catalog.dto;

/**
 * One configurable field of the Product Configuration screen (AC-SALE-01-10/17/20).
 *
 * <p>{@code dataType} is the raw workbook value (NUMBER, BOOLEAN, TEXT, DATE) — the
 * frontend maps it to an input control (AC-SALE-01-17) and the backend validates
 * against the same value (AC-SALE-01-19), so both sides read one source of truth.
 * {@code mandatory} drives the visual required-marker (AC-SALE-01-20) and the
 * MSG-VAL-CHAR-REQUIRED check.
 */
public record CharacteristicResponse(
        Long characteristicId,
        String name,
        String description,
        String dataType,
        boolean mandatory) {
}
