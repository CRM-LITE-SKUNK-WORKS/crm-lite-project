package com.crm.messaging.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {

    /**
     * The relay's work queue, oldest business fact first.
     *
     * <p>Ordered by {@code occurredAt} and not by insertion id: the ordering guarantee
     * this project promises is per-sale (the partition key), and occurrence order is the
     * one that matches what the business actually did.
     */
    @Query("""
            select m from OutboxMessage m
            where m.publishedAt is null
            order by m.occurredAt asc
            """)
    List<OutboxMessage> findUnpublished(Pageable pageable);

    long countByPublishedAtIsNull();

    /** Feeds the "oldest unpublished age" gauge — the metric that actually detects a stalled relay. */
    @Query("select min(m.occurredAt) from OutboxMessage m where m.publishedAt is null")
    Optional<Instant> findOldestUnpublishedOccurredAt();

    /** Retention removes ONLY rows already confirmed on the wire (ADR-017 §10). */
    @Modifying
    @Query("delete from OutboxMessage m where m.publishedAt is not null and m.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
