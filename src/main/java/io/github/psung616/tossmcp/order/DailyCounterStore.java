package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.config.AppPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * KST 날짜 기준 일일 주문 횟수·금액 (P4-1, ARCHITECTURE §8). DRY_RUN 주문은 실주문과 별도로 집계한다.
 *
 * <p>사용자 cache 폴더의 {@code daily-counters.json} 에 저장해 재시작해도 유지하고, {@code serve}·{@code web} 이 동시에 떠 있어도 같은 값을 쓰도록 JVM 잠금 +
 * 파일 잠금({@code daily-counters.json.lock}) 안에서 읽고·고치고·원자적으로 교체한다.
 *
 * <p>실패 정책 — <b>주문 전 검사는 닫힌 쪽으로</b>: 파일이 깨졌거나 읽을 수 없으면 {@link #today} 가 예외를 던져 미리보기가 거부된다(0 으로 초기화하면 일일
 * 한도를 넘을 수 있다). 주문 <b>후</b> 기록({@link #record})은 이미 나간 주문을 되돌릴 수 없으므로 실패해도 예외를 던지지 않고 오류 로그만 남긴다 — 이후
 * 미리보기는 파일 문제가 해결될 때까지 거부된다.
 */
@Component
public class DailyCounterStore {

    private static final Logger log = LoggerFactory.getLogger(DailyCounterStore.class);
    private static final Map<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    public record Snapshot(int count, BigDecimal amountKrw, Instant lastOrderAt) {}

    /** 파일 한 칸. 금액은 문자열로 저장해 정밀도를 지킨다. lastOrderAt 은 날짜가 바뀌어도 유지(최소 주문 간격용). */
    record Counter(String date, int count, String amountKrw, String lastOrderAt) {

        static final Counter EMPTY = new Counter(null, 0, "0", null);

        Counter rolled(LocalDate today) {
            return today.toString().equals(date) ? this : new Counter(today.toString(), 0, "0", lastOrderAt);
        }

        Snapshot snapshot() {
            return new Snapshot(
                    count, new BigDecimal(amountKrw), lastOrderAt == null ? null : Instant.parse(lastOrderAt));
        }
    }

    record CounterFile(Counter real, Counter dryRun) {

        static final CounterFile EMPTY = new CounterFile(Counter.EMPTY, Counter.EMPTY);

        Counter get(boolean dryRunMode) {
            Counter c = dryRunMode ? dryRun : real;
            return c == null ? Counter.EMPTY : c;
        }

        CounterFile with(boolean dryRunMode, Counter c) {
            return dryRunMode ? new CounterFile(get(false), c) : new CounterFile(c, get(true));
        }
    }

    /** 카운터 파일이 손상돼 주문 한도를 확인할 수 없음 — 미리보기를 거부한다. */
    public static class CounterFileException extends IllegalStateException {
        CounterFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Clock clock;
    private final Path file;
    private final Path lockFile;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    public DailyCounterStore(Clock clock, AppPaths appPaths) {
        this(clock, appPaths.cacheDir().resolve("daily-counters.json"));
    }

    DailyCounterStore(Clock clock, Path file) {
        this.clock = clock;
        this.file = file.toAbsolutePath();
        this.lockFile = this.file.resolveSibling(this.file.getFileName() + ".lock");
    }

    /** 오늘(KST) 집계. 파일이 깨졌거나 읽을 수 없으면 {@link CounterFileException} — 주문 전 검사를 닫힌 쪽으로. */
    public Snapshot today(boolean dryRunMode) {
        LocalDate today = LocalDate.now(clock);
        return withLock(() -> read().get(dryRunMode).rolled(today).snapshot());
    }

    /** 주문(또는 DRY_RUN 주문) 1건을 기록한다. 실패해도 예외를 던지지 않는다(주문은 이미 나갔다). */
    public void record(boolean dryRunMode, BigDecimal amountKrw) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        try {
            update(f -> {
                Counter c = f.get(dryRunMode).rolled(today);
                Counter next = new Counter(
                        c.date(),
                        c.count() + 1,
                        new BigDecimal(c.amountKrw()).add(amountKrw).toPlainString(),
                        now.toString());
                return f.with(dryRunMode, next);
            });
        } catch (RuntimeException e) {
            log.error(
                    "일일 주문 카운터 기록 실패 ({}). 파일 문제가 해결될 때까지 새 주문 미리보기는 거부됩니다.",
                    e.getClass().getSimpleName());
        }
    }

    private void update(UnaryOperator<CounterFile> change) {
        withLock(() -> {
            write(change.apply(read()));
            return null;
        });
    }

    private <T> T withLock(java.util.function.Supplier<T> action) {
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(file, p -> new ReentrantLock());
        jvmLock.lock();
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                return action.get();
            }
        } catch (IOException e) {
            throw new CounterFileException("일일 주문 카운터 파일을 잠그지 못했습니다.", e);
        } finally {
            jvmLock.unlock();
        }
    }

    /** 잠금 안에서. 파일이 없으면 빈 값, 깨졌으면 예외. */
    private CounterFile read() {
        if (!Files.exists(file)) {
            return CounterFile.EMPTY;
        }
        try {
            CounterFile f = jsonMapper.readValue(Files.readAllBytes(file), CounterFile.class);
            if (f == null) {
                throw new IllegalStateException("empty");
            }
            validate(f.get(false));
            validate(f.get(true));
            return f;
        } catch (IOException | RuntimeException e) {
            throw new CounterFileException(
                    "일일 주문 카운터 파일(cache/daily-counters.json)이 손상돼 주문 한도를 확인할 수 없습니다. "
                            + "주문 기록(audit.jsonl)을 확인한 뒤 파일을 지우면 다시 주문할 수 있습니다.",
                    e);
        }
    }

    private static void validate(Counter c) {
        if (c.count() < 0) {
            throw new IllegalStateException("negative count");
        }
        if (new BigDecimal(c.amountKrw()).signum() < 0) {
            throw new IllegalStateException("negative amount");
        }
        if (c.date() != null) {
            LocalDate.parse(c.date());
        }
        if (c.lastOrderAt() != null) {
            Instant.parse(c.lastOrderAt());
        }
    }

    /** 잠금 안에서. 임시 파일에 쓰고 원자적으로 교체한다. */
    private void write(CounterFile f) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, jsonMapper.writeValueAsBytes(f));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("일일 주문 카운터 저장 실패", e);
        }
    }
}
