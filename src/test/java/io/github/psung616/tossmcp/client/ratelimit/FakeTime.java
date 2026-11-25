package io.github.psung616.tossmcp.client.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 테스트용 시계 + 대기. sleep 하면 실제로 기다리지 않고 시계만 앞으로 간다. */
public final class FakeTime extends Clock implements Sleeper {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private Instant now;
    public final List<Duration> sleeps = new ArrayList<>();

    public FakeTime(Instant start) {
        this.now = start;
    }

    /** 예: "2026-11-25T09:05:00" (KST) */
    public static FakeTime atKst(String localDateTime) {
        return new FakeTime(
                java.time.LocalDateTime.parse(localDateTime).atZone(KST).toInstant());
    }

    @Override
    public synchronized void sleep(Duration d) {
        sleeps.add(d);
        now = now.plus(d);
    }

    public synchronized void advance(Duration d) {
        now = now.plus(d);
    }

    public synchronized Duration totalSlept() {
        return sleeps.stream().reduce(Duration.ZERO, Duration::plus);
    }

    @Override
    public synchronized Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return KST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
