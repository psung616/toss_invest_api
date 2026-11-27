package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.config.AppPaths;
import io.github.psung616.tossmcp.config.KillSwitch;
import io.github.psung616.tossmcp.config.TossProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardrailServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @TempDir
    Path home;

    private MarketHours marketHours = (currency, now) -> java.util.Optional.empty();

    private TossProperties props(boolean enableOrders, List<String> symbols, boolean allowMarket, boolean allowSell) {
        return new TossProperties(
                enableOrders,
                true,
                new BigDecimal("100000"),
                new BigDecimal("300000"),
                2,
                symbols,
                allowMarket,
                allowSell,
                false,
                0,
                Duration.ofMinutes(5));
    }

    private GuardrailService service(TossProperties props, DailyCounterStore counters) {
        KillSwitch killSwitch = new KillSwitch(new AppPaths(home, home, home));
        return new GuardrailService(props, killSwitch, counters, marketHours, clock);
    }

    private GuardrailService service(TossProperties props) {
        return service(props, new DailyCounterStore(clock, home.resolve("daily-counters.json")));
    }

    private static OrderCommand limitBuy(String symbol, long qty, String price) {
        return new OrderCommand(symbol, OrderSide.BUY, OrderType.LIMIT, qty, new BigDecimal(price));
    }

    @Test
    void allowsOrderWithinLimits() {
        GuardrailResult result = service(props(true, List.of("005930"), false, false))
                .evaluate(limitBuy("005930", 1, "70000"), "KRW", new BigDecimal("70000"));

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void defaultsRejectOrders() {
        GuardrailResult result = service(props(false, List.of(), false, false))
                .evaluate(limitBuy("005930", 1, "70000"), "KRW", new BigDecimal("70000"));

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("주문 기능이 꺼져"));
        assertThat(result.violations()).anyMatch(v -> v.contains("허용 종목이 아닙니다"));
    }

    @Test
    void collectsAllViolations() {
        OrderCommand marketSell = new OrderCommand("005930", OrderSide.SELL, OrderType.MARKET, 10, null);

        GuardrailResult result = service(props(true, List.of("005930"), false, false))
                .evaluate(marketSell, "KRW", new BigDecimal("700000"));

        assertThat(result.violations())
                .anyMatch(v -> v.contains("시장가"))
                .anyMatch(v -> v.contains("매도"))
                .anyMatch(v -> v.contains("1회 주문 한도"));
    }

    @Test
    void rejectsUsStocksByDefault() {
        GuardrailResult result = service(props(true, List.of("AAPL"), false, false))
                .evaluate(limitBuy("AAPL", 1, "200"), "USD", new BigDecimal("200"));

        assertThat(result.violations()).anyMatch(v -> v.contains("미국 주식"));
    }

    @Test
    void rejectsWhenDailyCountReached() {
        DailyCounterStore counters = new DailyCounterStore(clock, home.resolve("daily-counters.json"));
        counters.record(true, new BigDecimal("10000"));
        counters.record(true, new BigDecimal("10000"));

        GuardrailResult result = service(props(true, List.of("005930"), false, false), counters)
                .evaluate(limitBuy("005930", 1, "10000"), "KRW", new BigDecimal("10000"));

        assertThat(result.violations()).anyMatch(v -> v.contains("횟수"));
    }

    @Test
    void killFileRejectsEverything() throws IOException {
        Files.createFile(home.resolve("KILL"));

        GuardrailResult result = service(props(true, List.of("005930"), false, false))
                .evaluate(limitBuy("005930", 1, "70000"), "KRW", new BigDecimal("70000"));

        assertThat(result.violations()).anyMatch(v -> v.contains("킬 스위치"));
    }

    @Test
    void clampsMaxOrderBelowHardLimit() {
        TossProperties props = new TossProperties(
                true,
                true,
                new BigDecimal("500000000"),
                new BigDecimal("1"),
                1,
                List.of(),
                false,
                false,
                false,
                0,
                Duration.ofMinutes(5));

        assertThat(props.maxOrderKrw()).isEqualByComparingTo(TossProperties.HARD_MAX_ORDER_KRW);
    }

    @Test
    void rejectsWhenMarketClosed() {
        marketHours = (currency, now) -> java.util.Optional.of("지금은 국내 주식 주문 가능 시간이 아닙니다.");

        GuardrailResult result = service(props(true, List.of("005930"), false, false))
                .evaluate(limitBuy("005930", 1, "70000"), "KRW", new BigDecimal("70000"));

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("주문 가능 시간"));
    }
}
