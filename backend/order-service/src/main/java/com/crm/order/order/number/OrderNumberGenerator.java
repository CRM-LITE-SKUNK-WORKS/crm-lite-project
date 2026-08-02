package com.crm.order.order.number;

import java.time.Clock;
import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * KR-12 Order Number generator (ADR-016 §4) — <b>a project-proposed rule awaiting
 * analyst sign-off</b>, recorded at the same level as the invented offer prices.
 *
 * <p>{@code [T][YY][SSSSSS][C]} as a 10-digit string, deliberately the exact shape
 * KR-11 defines for account numbers: AC-SALE-01-12 needs a displayable Order ID and
 * the system already has a proven, documented identifier scheme. {@code T} is fixed
 * {@code 1} for NEWSALE this phase; other values stay reserved for TRANSFER/CANCEL
 * should they ever gain an FR.
 *
 * <p><b>Duplicated from account-service on purpose</b> (ADR-016 §4.5): this project
 * has no shared business-logic library, and creating one for two classes would
 * couple two services' release cycles to make ~60 lines DRY. The two copies are
 * pinned by identical unit-test vectors.
 *
 * <p><b>Known consequence</b> (ADR-016 §8.1): with the same segment and start value,
 * order and account numbers are drawn from an identical value space — the seeded
 * order number {@code 1261000002} equals account {@code 1261000002}'s. Accepted:
 * different databases, different services, no shared namespace, and KR-02
 * disambiguates by giving the customer search two separate fields.
 *
 * <p>Must be called inside the transaction that persists the order: a rollback rolls
 * the sequence increment back too (gapless), and the taken row lock serializes
 * allocation per (segment, year).
 */
@Component
public class OrderNumberGenerator {

    static final int SEQUENCE_START = 100_000;
    static final int SEQUENCE_MAX = 999_999;

    /**
     * First caller for a fresh (segment, year): the INSERT arm claims 100000 and
     * stores next_value=100001 — RETURNING next_value - 1 yields exactly 100000, so
     * the invariant holds from the very first allocation (no off-by-one). Every
     * later caller conflicts into the atomic increment.
     */
    private static final String ALLOCATE_SQL = """
            INSERT INTO order_number_seq (segment, seq_year, next_value)
            VALUES (?, ?, ?)
            ON CONFLICT (segment, seq_year)
            DO UPDATE SET next_value = order_number_seq.next_value + 1
            RETURNING next_value - 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OrderNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** Allocates and formats the next number for the segment, using the clock's year. */
    public String next(int segment) {
        int year = Year.now(clock).getValue();
        int issued = allocate(segment, year);
        return format(segment, year, issued);
    }

    /**
     * Race-safe sequence allocation for an explicit (segment, year). Public so the
     * integration suite can prove first-value/isolation/concurrency behaviour for
     * arbitrary years without faking the clock at the bean level; production code
     * only ever calls {@link #next(int)}.
     */
    public int allocate(int segment, int year) {
        Integer issued = jdbcTemplate.queryForObject(ALLOCATE_SQL, Integer.class, segment, year, SEQUENCE_START + 1);
        if (issued == null || issued > SEQUENCE_MAX) {
            throw new OrderNumberCapacityExceededException(segment, year);
        }
        return issued;
    }

    /** Pure formatting: [T][YY][SSSSSS] payload + Luhn check digit (unit-testable). */
    static String format(int segment, int year, int sequenceValue) {
        String payload = "%d%02d%06d".formatted(segment, year % 100, sequenceValue);
        return payload + LuhnCheckDigit.compute(payload);
    }
}
