package com.crm.order.common.config;

import com.crm.order.saga.SaleSagaMetrics;
import com.crm.order.saga.SaleSagaProperties;
import com.crm.order.saga.SaleSagaRecoveryJob;
import com.crm.order.saga.SaleSagaRepository;
import com.crm.order.saga.SaleSagaScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the SALE saga (ADR-018).
 *
 * <p>The metrics and the scheduler are {@code @Bean}s rather than {@code @Component}s
 * because both need construction arguments that are not injectable as a bean of their own
 * type — a {@code MeterRegistry} plus repository and properties for the first, and an
 * explicit lifecycle for the second. The state machine, its repository and the recovery
 * job are ordinary components; only the two things that need assembling are assembled
 * here.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SaleSagaProperties.class)
public class SaleSagaConfig {

    @Bean
    public SaleSagaMetrics saleSagaMetrics(MeterRegistry registry, SaleSagaRepository repository,
                                           SaleSagaProperties properties, Clock clock) {
        return new SaleSagaMetrics(registry, repository, properties, clock);
    }

    @Bean
    public SaleSagaScheduler saleSagaScheduler(SaleSagaRecoveryJob recoveryJob, SaleSagaProperties properties) {
        return new SaleSagaScheduler(recoveryJob, properties);
    }
}
