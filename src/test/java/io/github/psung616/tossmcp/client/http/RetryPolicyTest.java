package io.github.psung616.tossmcp.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final RandomGenerator NO_JITTER = () -> 0L;

    @Test
    void exponentialBackoffWithoutRetryAfter() {
        RetryPolicy policy = new RetryPolicy(NO_JITTER);
        assertThat(policy.delayFor(1, null)).contains(Duration.ofSeconds(1));
        assertThat(policy.delayFor(2, null)).contains(Duration.ofSeconds(2));
        assertThat(policy.delayFor(3, null)).contains(Duration.ofSeconds(4));
    }

    @Test
    void retryAfterWinsWhenLonger() {
        RetryPolicy policy = new RetryPolicy(NO_JITTER);
        assertThat(policy.delayFor(1, 3)).contains(Duration.ofSeconds(3));
        assertThat(policy.delayFor(3, 1)).contains(Duration.ofSeconds(4));
    }

    @Test
    void retryAfterIsCapped() {
        RetryPolicy policy = new RetryPolicy(NO_JITTER);
        assertThat(policy.delayFor(1, 3600)).contains(RetryPolicy.MAX_DELAY);
    }

    @Test
    void givesUpAfterMaxRetries() {
        RetryPolicy policy = new RetryPolicy(NO_JITTER);
        assertThat(policy.delayFor(RetryPolicy.MAX_RETRIES + 1, null)).isEmpty();
    }

    @Test
    void jitterAddsAtMostHalfOfBase() {
        RetryPolicy policy = new RetryPolicy(RandomGenerator.of("L64X128MixRandom"));
        for (int i = 0; i < 200; i++) {
            Duration d = policy.delayFor(2, null).orElseThrow();
            assertThat(d).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
        }
    }
}
