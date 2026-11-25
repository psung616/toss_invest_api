package io.github.psung616.tossmcp.client.http;

import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * 429 재시도 간격. 공식 권장(overview.md): Retry-After 만큼 대기 + 지수 백오프(1s → 2s → 4s) + 지터.
 *
 * <p>대기 = max(Retry-After, 1s × 2^(n-1)) + 지터(0 ~ 백오프의 절반), 최대 {@link #MAX_DELAY}. 최대 {@link #MAX_RETRIES}회.
 */
@Component
public class RetryPolicy {

    static final int MAX_RETRIES = 3;
    static final Duration MAX_DELAY = Duration.ofSeconds(30);
    private static final long BASE_MILLIS = 1_000L;

    private final RandomGenerator random;

    RetryPolicy() {
        this(RandomGenerator.getDefault());
    }

    RetryPolicy(RandomGenerator random) {
        this.random = random;
    }

    /**
     * @param attempt           몇 번째 재시도인지 (1부터)
     * @param retryAfterSeconds 429 응답의 Retry-After(초). 없으면 null
     * @return 기다릴 시간. 더 재시도하지 않으면 빈 값
     */
    Optional<Duration> delayFor(int attempt, Integer retryAfterSeconds) {
        if (attempt < 1 || attempt > MAX_RETRIES) {
            return Optional.empty();
        }
        long backoff = BASE_MILLIS << (attempt - 1);
        long retryAfter = retryAfterSeconds == null ? 0 : Math.max(0, retryAfterSeconds) * 1_000L;
        long jitter = random.nextLong(backoff / 2 + 1);
        long millis = Math.min(Math.max(backoff, retryAfter) + jitter, MAX_DELAY.toMillis());
        return Optional.of(Duration.ofMillis(millis));
    }
}
