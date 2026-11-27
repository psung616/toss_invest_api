package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.psung616.tossmcp.order.DailyCounterStore.CounterFileException;
import io.github.psung616.tossmcp.order.DailyCounterStore.Snapshot;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P4-1 일일 카운터 파일 영속화: 재시작 유지, KST 날짜 리셋, DRY_RUN 별도, 손상 시 닫힌 쪽, 동시 기록. 금액은 지어낸 값. */
class DailyCounterStoreTest {

    @TempDir
    Path dir;

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(String instant) {
            now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // 2026-11-27 10:00 KST
    private final MutableClock clock = new MutableClock("2026-11-27T01:00:00Z");

    private Path file() {
        return dir.resolve("cache").resolve("daily-counters.json");
    }

    private DailyCounterStore store() {
        return new DailyCounterStore(clock, file());
    }

    @Test
    void emptyWhenNoFile() {
        Snapshot s = store().today(false);

        assertThat(s.count()).isZero();
        assertThat(s.amountKrw()).isEqualByComparingTo("0");
        assertThat(s.lastOrderAt()).isNull();
    }

    @Test
    void survivesRestartAndKeepsDecimalPrecision() {
        store().record(false, new BigDecimal("50000.5"));
        store().record(false, new BigDecimal("0.25"));

        Snapshot s = store().today(false); // 새 인스턴스 = 재시작

        assertThat(s.count()).isEqualTo(2);
        assertThat(s.amountKrw()).isEqualByComparingTo("50000.75");
        assertThat(s.lastOrderAt()).isEqualTo(clock.now);
    }

    @Test
    void dryRunCountedSeparately() {
        DailyCounterStore store = store();
        store.record(true, new BigDecimal("10000"));

        assertThat(store.today(true).count()).isEqualTo(1);
        assertThat(store.today(false).count()).isZero();
    }

    @Test
    void resetsAtKstMidnightButKeepsLastOrderTime() {
        DailyCounterStore store = store();
        store.record(false, new BigDecimal("10000"));
        Instant ordered = clock.now;

        clock.now = Instant.parse("2026-11-27T14:59:59Z"); // 23:59:59 KST — 같은 날
        assertThat(store.today(false).count()).isEqualTo(1);

        clock.now = Instant.parse("2026-11-27T15:00:00Z"); // 다음 날 00:00 KST
        Snapshot next = store.today(false);
        assertThat(next.count()).isZero();
        assertThat(next.amountKrw()).isEqualByComparingTo("0");
        assertThat(next.lastOrderAt()).as("최소 주문 간격은 자정을 넘어도 유지").isEqualTo(ordered);

        store.record(false, new BigDecimal("3000"));
        assertThat(store.today(false).count()).isEqualTo(1);
        assertThat(store.today(false).amountKrw()).isEqualByComparingTo("3000");
    }

    @Test
    void corruptFileFailsClosedForPreviewButRecordDoesNotThrow() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{not json");
        DailyCounterStore store = store();

        assertThatThrownBy(() -> store.today(false))
                .isInstanceOf(CounterFileException.class)
                .hasMessageContaining("손상");
        store.record(false, new BigDecimal("1000")); // 주문 후 기록은 예외 없이 로그만
        assertThat(Files.readString(file())).as("손상 파일을 덮어써 0 으로 만들지 않는다").isEqualTo("{not json");
    }

    @Test
    void tamperedNegativeValuesAreTreatedAsCorrupt() throws Exception {
        Files.createDirectories(file().getParent());
        Files.writeString(
                file(),
                "{\"real\":{\"date\":\"2026-11-27\",\"count\":-5,\"amountKrw\":\"0\",\"lastOrderAt\":null},\"dryRun\":null}");

        assertThatThrownBy(() -> store().today(false)).isInstanceOf(CounterFileException.class);
    }

    @Test
    void concurrentRecordsFromTwoInstancesAreAllCounted() throws Exception {
        DailyCounterStore a = store();
        DailyCounterStore b = store(); // 다른 프로세스 역할 (같은 파일)
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                DailyCounterStore s = i % 2 == 0 ? a : b;
                jobs.add(() -> {
                    s.record(false, new BigDecimal("1000"));
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(jobs)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        Snapshot s = store().today(false);
        assertThat(s.count()).isEqualTo(40);
        assertThat(s.amountKrw()).isEqualByComparingTo("40000");
    }

    @Test
    void fileHoldsNoAccountOrKeyData() throws Exception {
        store().record(false, new BigDecimal("12345"));

        String json = Files.readString(file());
        assertThat(json).contains("\"count\":1").doesNotContain("accountSeq").doesNotContain("symbol");
    }
}
