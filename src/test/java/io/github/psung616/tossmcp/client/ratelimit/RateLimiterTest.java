package io.github.psung616.tossmcp.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void burstUpToLimitThenPacesAtLimitPerSecond() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);

        for (int i = 0; i < 15; i++) {
            limiter.acquire(RateLimitGroup.MARKET_DATA);
        }
        assertThat(time.sleeps).isEmpty();

        limiter.acquire(RateLimitGroup.MARKET_DATA);
        assertThat(time.sleeps).hasSize(1);
        assertThat(time.sleeps.get(0)).isEqualTo(Duration.ofNanos(66_666_000));
    }

    @Test
    void tokenBucketSemanticsBurstThenSteadyRate() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);
        java.util.List<java.time.Instant> sent = new java.util.ArrayList<>();

        for (int i = 0; i < 40; i++) {
            limiter.acquire(RateLimitGroup.ORDER_HISTORY);
            sent.add(time.instant());
        }
        // 서버와 같은 토큰버킷: 용량 5 를 처음에 몰아 쓰고, 이후에는 0.2초마다 1개 (초당 5개)
        for (int i = 1; i < 5; i++) {
            assertThat(sent.get(i)).isEqualTo(sent.get(0));
        }
        for (int i = 5; i < sent.size(); i++) {
            assertThat(Duration.between(sent.get(i - 1), sent.get(i))).isEqualTo(Duration.ofMillis(200));
        }
        // 버스트 이후 어떤 1초 구간에도 5개를 넘게 보내지 않는다
        assertThat(Duration.between(sent.get(5), sent.get(39))).isEqualTo(Duration.ofMillis(200L * 34));
    }

    @Test
    void groupsAreIndependent() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);

        limiter.acquire(RateLimitGroup.ACCOUNT);
        limiter.acquire(RateLimitGroup.STOCK_ALL);
        assertThat(time.sleeps).isEmpty();

        limiter.acquire(RateLimitGroup.ACCOUNT);
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void orderInfoDropsToThreeDuringPeakAndRecoversAfter() {
        FakeTime time = FakeTime.atKst("2026-11-25T09:05:00");
        RateLimiter limiter = new RateLimiter(time, time);
        assertThat(limiter.effectiveLimit(RateLimitGroup.ORDER_INFO)).isEqualTo(3);
        assertThat(limiter.effectiveLimit(RateLimitGroup.ORDER)).isEqualTo(10);

        for (int i = 0; i < 3; i++) {
            limiter.acquire(RateLimitGroup.ORDER_INFO);
        }
        assertThat(time.sleeps).isEmpty();
        limiter.acquire(RateLimitGroup.ORDER_INFO);
        assertThat(time.sleeps).containsExactly(Duration.ofNanos(333_333_000));

        time.advance(Duration.ofMinutes(5)); // 09:10 이후
        assertThat(limiter.effectiveLimit(RateLimitGroup.ORDER_INFO)).isEqualTo(6);
    }

    @Test
    void peakWindowBoundaries() {
        assertThat(new RateLimiter(FakeTime.atKst("2026-11-25T08:59:59"), Sleeper.NONE)
                        .effectiveLimit(RateLimitGroup.ORDER_INFO))
                .isEqualTo(6);
        assertThat(new RateLimiter(FakeTime.atKst("2026-11-25T09:00:00"), Sleeper.NONE)
                        .effectiveLimit(RateLimitGroup.ORDER_INFO))
                .isEqualTo(3);
        assertThat(new RateLimiter(FakeTime.atKst("2026-11-25T09:10:00"), Sleeper.NONE)
                        .effectiveLimit(RateLimitGroup.ORDER_INFO))
                .isEqualTo(6);
    }

    @Test
    void serverHeaderCanOnlyLowerTheLimitAndExpires() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);

        limiter.observeLimit(RateLimitGroup.MARKET_DATA, "5");
        assertThat(limiter.effectiveLimit(RateLimitGroup.MARKET_DATA)).isEqualTo(5);

        limiter.observeLimit(RateLimitGroup.STOCK, "50");
        assertThat(limiter.effectiveLimit(RateLimitGroup.STOCK)).isEqualTo(5);

        time.advance(RateLimiter.HEADER_TTL);
        assertThat(limiter.effectiveLimit(RateLimitGroup.MARKET_DATA)).isEqualTo(15);
    }

    @Test
    void invalidHeaderIgnored() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);
        limiter.observeLimit(RateLimitGroup.MARKET_DATA, null);
        limiter.observeLimit(RateLimitGroup.MARKET_DATA, "abc");
        limiter.observeLimit(RateLimitGroup.MARKET_DATA, "0");
        limiter.observeLimit(RateLimitGroup.MARKET_DATA, "-3");
        assertThat(limiter.effectiveLimit(RateLimitGroup.MARKET_DATA)).isEqualTo(15);
    }

    @Test
    void loweredLimitSlowsPacing() {
        FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
        RateLimiter limiter = new RateLimiter(time, time);
        limiter.observeLimit(RateLimitGroup.MARKET_DATA, "1");

        limiter.acquire(RateLimitGroup.MARKET_DATA);
        limiter.acquire(RateLimitGroup.MARKET_DATA);
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(1));
    }
}
