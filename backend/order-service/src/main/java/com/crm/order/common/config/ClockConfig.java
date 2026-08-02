package com.crm.order.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Injectable clock (template parity with the other domain services). Production
 * uses UTC; tests override this bean with fixed clocks for any date-dependent
 * behaviour (e.g. future campaign activation-window rules in the §2.7 sale slice).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
