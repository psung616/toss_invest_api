package io.github.psung616.tossmcp.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * KST 날짜 기준 일일 주문 횟수·금액. DRY_RUN 주문은 실주문과 별도로 집계한다.
 *
 * TODO(P4-1): cache/daily-counters.json 파일 + 파일 잠금으로 영속화 (현재는 재시작 시 초기화)
 */
@Component
public class DailyCounterStore {

    private final Clock clock;
    private final Counter real = new Counter();
    private final Counter dryRun = new Counter();

    public DailyCounterStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized Snapshot today(boolean dryRunMode) {
        Counter c = counter(dryRunMode);
        c.rollIfNeeded(LocalDate.now(clock));
        return new Snapshot(c.count, c.amountKrw, c.lastOrderAt);
    }

    public synchronized void record(boolean dryRunMode, BigDecimal amountKrw) {
        Counter c = counter(dryRunMode);
        c.rollIfNeeded(LocalDate.now(clock));
        c.count++;
        c.amountKrw = c.amountKrw.add(amountKrw);
        c.lastOrderAt = clock.instant();
    }

    private Counter counter(boolean dryRunMode) {
        return dryRunMode ? dryRun : real;
    }

    public record Snapshot(int count, BigDecimal amountKrw, Instant lastOrderAt) {}

    private static final class Counter {
        private LocalDate date;
        private int count;
        private BigDecimal amountKrw = BigDecimal.ZERO;
        private Instant lastOrderAt;

        void rollIfNeeded(LocalDate today) {
            if (!today.equals(date)) {
                date = today;
                count = 0;
                amountKrw = BigDecimal.ZERO;
            }
        }
    }
}
