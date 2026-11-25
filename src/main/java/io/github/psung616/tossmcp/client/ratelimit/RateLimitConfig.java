package io.github.psung616.tossmcp.client.ratelimit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RateLimitConfig {

    @Bean
    Sleeper sleeper() {
        return Sleeper.system();
    }

    @Bean
    RateLimiter rateLimiter(Clock clock, Sleeper sleeper) {
        return new RateLimiter(clock, sleeper);
    }
}
