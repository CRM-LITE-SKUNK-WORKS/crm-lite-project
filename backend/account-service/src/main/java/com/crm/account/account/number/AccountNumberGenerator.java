package com.crm.account.account.number;

import java.time.Clock;
import java.time.Year;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * KR-11 Account Number generator (ADR-014): {@code [T][YY][SSSSSS][C]} as a 10-digit
 * string. The creation year comes from the injectable {@link Clock}; the sequence is
 * allocated with a single race-safe upsert against {@code acct_number_seq}, whose
 * {@code next_value} column always holds the NEXT value that will be issued.
 *
 * <p>Must be called inside the transaction that persists the account: a rollback
 * rolls the sequence increment back too (gapless), and the taken row lock
 * serializes creation per (segment, year) — the accepted ADR-014 trade-off.
 */
@Component
public class AccountNumberGenerator {

    static final int SEQUENCE_START = 100_000;
    static final int SEQUENCE_MAX = 999_999;

    /**
     * First caller for a fresh (segment, year): the INSERT arm claims 100000 and
     * stores next_value=100001 — RETURNING next_value - 1 yields exactly 100000, so
     * the invariant holds from the very first allocation (no off-by-one). Every
     * later caller conflicts into the atomic increment.
     */
    private static final String ALLOCATE_SQL = """
            INSERT INTO acct_number_seq (segment, seq_year, next_value)
            VALUES (?, ?, ?)
            ON CONFLICT (segment, seq_year)
            DO UPDATE SET next_value = acct_number_seq.next_value + 1
            RETURNING next_value - 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public AccountNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
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
            throw new AccountNumberCapacityExceededException(segment, year);
        }
        return issued;
    }

    /** Pure formatting: [T][YY][SSSSSS] payload + Luhn check digit (unit-testable). */
    static String format(int segment, int year, int sequenceValue) {
        String payload = "%d%02d%06d".formatted(segment, year % 100, sequenceValue);
        return payload + LuhnCheckDigit.compute(payload);
    }
}
