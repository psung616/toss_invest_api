package io.github.psung616.tossmcp.client.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * 그룹별 클라이언트 측 레이트리미터. 한도를 넘으면 거부하지 않고 차례가 올 때까지 기다린다.
 *
 * <p>알고리즘은 GCRA(토큰버킷과 동일한 동작): 용량 = 초당 한도(버스트), 재충전 = 초당 한도. 정수(마이크로초)로만 계산한다. 슬롯을 잠금 안에서
 * 예약하고 대기는 잠금 밖에서 하므로 여러 스레드가 동시에 불러도 순서대로 간격이 벌어진다.
 *
 * <p>이 프로세스 안에서만 제한한다. 여러 프로세스(serve 여러 개 등)가 동시에 호출하면 서버 429 가 날 수 있고, 그 경우는 TossHttpClient 의 재시도가
 * 처리한다.
 */
public class RateLimiter {

    /** 서버 헤더로 낮춘 한도를 믿는 시간. 이후에는 표의 한도로 돌아간다(피크 시간 값이 하루 종일 남지 않도록). */
    public static final Duration HEADER_TTL = Duration.ofSeconds(60);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime PEAK_START = LocalTime.of(9, 0);
    private static final LocalTime PEAK_END = LocalTime.of(9, 10);
    private static final long MICROS_PER_SECOND = 1_000_000L;

    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<RateLimitGroup, Bucket> buckets = new EnumMap<>(RateLimitGroup.class);

    public RateLimiter(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
        for (RateLimitGroup g : RateLimitGroup.values()) {
            buckets.put(g, new Bucket());
        }
    }

    /** 이 그룹으로 요청 1개를 보낼 차례가 될 때까지 기다린다. */
    public void acquire(RateLimitGroup group) {
        long waitMicros = reserve(group);
        if (waitMicros > 0) {
            sleeper.sleep(Duration.ofNanos(waitMicros * 1_000L));
        }
    }

    /** 응답 헤더 {@code X-RateLimit-Limit} 값을 반영한다. 표보다 작을 때만 효과가 있고, 잘못된 값은 무시한다. */
    public void observeLimit(RateLimitGroup group, String headerValue) {
        if (headerValue == null) {
            return;
        }
        int value;
        try {
            value = Integer.parseInt(headerValue.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (value <= 0) {
            return;
        }
        Bucket b = buckets.get(group);
        synchronized (b) {
            b.headerLimit = value;
            b.headerObservedAt = clock.instant();
        }
    }

    /** 지금 적용 중인 초당 한도. */
    public int effectiveLimit(RateLimitGroup group) {
        Bucket b = buckets.get(group);
        synchronized (b) {
            return effectiveLimit(group, b, clock.instant());
        }
    }

    private long reserve(RateLimitGroup group) {
        Bucket b = buckets.get(group);
        synchronized (b) {
            Instant nowInstant = clock.instant();
            long now = toMicros(nowInstant);
            int limit = effectiveLimit(group, b, nowInstant);
            long interval = MICROS_PER_SECOND / limit;
            long tolerance = interval * (limit - 1);
            long tat = Math.max(b.theoreticalArrival, now);
            long wait = Math.max(0, tat - tolerance - now);
            b.theoreticalArrival = tat + interval;
            return wait;
        }
    }

    private int effectiveLimit(RateLimitGroup group, Bucket b, Instant now) {
        LocalTime t = now.atZone(KST).toLocalTime();
        boolean peak = !t.isBefore(PEAK_START) && t.isBefore(PEAK_END);
        int table = peak ? group.peakLimit() : group.limit();
        if (b.headerLimit > 0 && now.isBefore(b.headerObservedAt.plus(HEADER_TTL))) {
            return Math.min(table, b.headerLimit);
        }
        return table;
    }

    private static long toMicros(Instant i) {
        return i.getEpochSecond() * MICROS_PER_SECOND + i.getNano() / 1_000L;
    }

    private static final class Bucket {
        long theoreticalArrival = Long.MIN_VALUE / 2;
        int headerLimit;
        Instant headerObservedAt = Instant.EPOCH;
    }
}
