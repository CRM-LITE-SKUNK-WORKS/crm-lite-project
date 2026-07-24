package com.crm.account.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injectable clock for KR-11 year-dependent Account Number generation (ADR-014 §5).
 * Production uses UTC; tests override this bean with fixed clocks to pin the
 * creation year (including year-rollover scenarios).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
