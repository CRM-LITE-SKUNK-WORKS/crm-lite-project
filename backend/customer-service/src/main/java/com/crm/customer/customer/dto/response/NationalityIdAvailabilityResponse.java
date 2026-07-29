package com.crm.customer.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Answer of the Nationality-ID availability probe (ADR-005 §Addendum 2026-07-29).
 *
 * Deliberately carries ONE boolean and nothing else. The rule it reports covers
 * soft-deleted customers too (ADR-003), so any extra field — a customer number, a
 * name, a status, even an echo of the queried id — would turn a yes/no check into a
 * way of mining who once existed. {@code available == false} says the ID is taken; it
 * never says by whom.
 */
@Getter
@AllArgsConstructor
public class NationalityIdAvailabilityResponse {

    private final boolean available;
}
