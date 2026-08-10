package com.crm.messaging;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.messaging.inbox.InboxGuard;
import com.crm.messaging.inbox.InboxMessageRepository;
import com.crm.messaging.inbox.InboxMetrics;
import com.crm.messaging.outbox.NoBrokerOutboxPublisher;
import com.crm.messaging.outbox.OutboxMessageRepository;
import com.crm.messaging.outbox.OutboxMetrics;
import com.crm.messaging.outbox.OutboxProperties;
import com.crm.messaging.outbox.OutboxPublisher;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.messaging.outbox.OutboxRelay;
import com.crm.messaging.outbox.OutboxRetentionJob;
import com.crm.messaging.outbox.OutboxScheduler;
import com.crm.messaging.outbox.OutboxStatusWriter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the messaging foundation (ADR-017).
 *
 * <p>Two things it deliberately does NOT do:
 *
 * <ol>
 *   <li><b>No {@code @EntityScan}/{@code @EnableJpaRepositories}.</b> Registering entity
 *       packages from an auto-configuration would REPLACE Boot's auto-configuration
 *       package scan rather than add to it, so every service's own entities would silently
 *       stop being found. Each service therefore lists both packages explicitly on its
 *       application class — visible, and impossible to get half-right.</li>
 *   <li><b>No {@code @EnableScheduling}.</b> Turning Spring scheduling on for every
 *       consumer of this starter is a behaviour change far outside this module's remit;
 *       {@link OutboxScheduler} runs its own single thread instead.</li>
 *   <li><b>No reference to Spring Cloud Stream</b> — not even to wire the publisher bean.
 *       That lives in {@code com.crm.messaging.adapter.stream.StreamAdapterAutoConfiguration},
 *       so ADR-017 §4's "Spring Cloud Stream appears only in adapter packages" is literally
 *       true of the import graph rather than true-except-for-the-wiring.</li>
 * </ol>
 *
 * <p>Ordered after {@link HibernateJpaAutoConfiguration} because the recorder, guard and
 * relay all need repositories and an {@code EntityManager} to exist first.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(OutboxMessageRepository.class)
@EnableConfigurationProperties(OutboxProperties.class)
public class CrmMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EnvelopeCodec envelopeCodec() {
        return new EnvelopeCodec();
    }

    /** Services already declare a {@code Clock} bean (ClockConfig); this is only a fallback. */
    @Bean
    @ConditionalOnMissingBean
    public Clock messagingClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxMetrics outboxMetrics(MeterRegistry registry, OutboxMessageRepository repository, Clock clock) {
        return new OutboxMetrics(registry, repository, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxMetrics inboxMetrics(MeterRegistry registry) {
        return new InboxMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRecorder outboxRecorder(OutboxMessageRepository repository, EnvelopeCodec codec,
                                         OutboxProperties properties, Clock clock) {
        return new OutboxRecorder(repository, codec, properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxStatusWriter outboxStatusWriter(OutboxMessageRepository repository, Clock clock) {
        return new OutboxStatusWriter(repository, clock);
    }

    /**
     * The fallback publisher, used whenever no broker adapter registered one — which
     * before the SALE cutover is always ({@code crm.messaging.broker.enabled=false}).
     *
     * <p>It throws rather than discarding, so an operator who enables the relay without
     * enabling the broker gets a visible backlog and a failure count instead of silently
     * losing every message. The real adapter lives in
     * {@code com.crm.messaging.adapter.stream.StreamAdapterAutoConfiguration}, which is
     * ordered before this class so its bean wins the {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public OutboxPublisher noBrokerOutboxPublisher() {
        return new NoBrokerOutboxPublisher();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(OutboxMessageRepository repository, OutboxPublisher publisher,
                                   OutboxStatusWriter statusWriter, EnvelopeCodec codec,
                                   OutboxProperties properties, OutboxMetrics metrics) {
        return new OutboxRelay(repository, publisher, statusWriter, codec, properties, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRetentionJob outboxRetentionJob(OutboxMessageRepository repository,
                                                 OutboxProperties properties, OutboxMetrics metrics,
                                                 Clock clock) {
        return new OutboxRetentionJob(repository, properties, metrics, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxScheduler outboxScheduler(OutboxRelay relay, OutboxRetentionJob retentionJob,
                                           OutboxProperties properties) {
        return new OutboxScheduler(relay, retentionJob, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InboxMessageRepository.class)
    public InboxGuard inboxGuard(InboxMessageRepository repository, EntityManager entityManager,
                                 InboxMetrics metrics, Clock clock) {
        return new InboxGuard(repository, entityManager, metrics, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InboxMessageRepository.class)
    public InboxDispatcher inboxDispatcher(InboxGuard guard, EnvelopeCodec codec, InboxMetrics metrics,
                                           OutboxPublisher publisher) {
        return new InboxDispatcher(guard, codec, metrics, publisher);
    }
}
