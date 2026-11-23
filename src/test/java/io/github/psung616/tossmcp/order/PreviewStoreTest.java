package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.config.TossProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewStoreTest {

    private final OrderCommand command =
            new OrderCommand("005930", OrderSide.BUY, OrderType.LIMIT, 1, new BigDecimal("70000"));

    private static TossProperties props() {
        return new TossProperties(
                true,
                true,
                new BigDecimal("100000"),
                new BigDecimal("300000"),
                10,
                List.of(),
                false,
                false,
                false,
                0,
                Duration.ofMinutes(5));
    }

    @Test
    void previewIdCanBeUsedOnlyOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        PreviewStore store = new PreviewStore(clock, props());
        String id = store.save(command, "KRW", new BigDecimal("70000"), new BigDecimal("70000"))
                .previewId();

        assertThat(store.take(id)).isPresent();
        assertThat(store.take(id)).isEmpty();
    }

    @Test
    void expiredPreviewIsRejected() {
        Instant start = Instant.parse("2026-11-23T01:00:00Z");
        MutableClock clock = new MutableClock(start);
        PreviewStore store = new PreviewStore(clock, props());
        String id = store.save(command, "KRW", new BigDecimal("70000"), new BigDecimal("70000"))
                .previewId();

        clock.now = start.plus(Duration.ofMinutes(6));

        assertThat(store.take(id)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
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
}
