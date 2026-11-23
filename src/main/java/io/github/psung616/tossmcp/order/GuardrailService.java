package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.config.KillSwitch;
import io.github.psung616.tossmcp.config.TossProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 주문 전 반드시 통과해야 하는 검사 (ARCHITECTURE §8). LLM 프롬프트가 아니라 코드로 강제한다.
 *
 * TODO(P4-1): 7번 장 운영 시간 검사(market-calendar), 파일 기반 일일 카운터
 */
@Service
public class GuardrailService {

    private final TossProperties properties;
    private final KillSwitch killSwitch;
    private final DailyCounterStore counters;
    private final Clock clock;

    public GuardrailService(TossProperties properties, KillSwitch killSwitch, DailyCounterStore counters, Clock clock) {
        this.properties = properties;
        this.killSwitch = killSwitch;
        this.counters = counters;
        this.clock = clock;
    }

    /**
     * @param currency       종목 통화 (KRW / USD)
     * @param estimatedKrw   원화 기준 예상 주문 금액
     */
    public GuardrailResult evaluate(OrderCommand command, String currency, BigDecimal estimatedKrw) {
        List<String> violations = new ArrayList<>();

        if (killSwitch.isActive()) {
            violations.add("킬 스위치가 켜져 있어 모든 주문이 거부됩니다. (" + killSwitch.killFile() + " 또는 TOSSINVEST_KILL)");
        }
        if (!properties.enableOrders()) {
            violations.add("주문 기능이 꺼져 있습니다. (TOSSINVEST_ENABLE_ORDERS=false)");
        }
        if (!properties.isSymbolAllowed(command.symbol())) {
            violations.add("허용 종목이 아닙니다: " + command.symbol() + " (TOSSINVEST_ALLOWED_SYMBOLS, 비어 있으면 주문 불가)");
        }
        if ("USD".equals(currency)) {
            if (!properties.allowUs()) {
                violations.add("미국 주식 주문이 비활성화되어 있습니다. (TOSSINVEST_ALLOW_US=false)");
            } else {
                violations.add("미국 주식의 원화 환산(P1-7)이 아직 구현되지 않아 주문할 수 없습니다.");
            }
        }
        if (command.type() == OrderType.MARKET && !properties.allowMarketOrders()) {
            violations.add("시장가 주문이 비활성화되어 있습니다. 지정가로 주문하세요. (TOSSINVEST_ALLOW_MARKET_ORDERS=false)");
        }
        if (command.side() == OrderSide.SELL && !properties.allowSell()) {
            violations.add("매도 주문이 비활성화되어 있습니다. (TOSSINVEST_ALLOW_SELL=false)");
        }
        if (estimatedKrw.compareTo(properties.maxOrderKrw()) > 0) {
            violations.add("1회 주문 한도 초과: 예상 %s원 > 한도 %s원 (TOSSINVEST_MAX_ORDER_KRW)"
                    .formatted(
                            estimatedKrw.toPlainString(),
                            properties.maxOrderKrw().toPlainString()));
        }

        DailyCounterStore.Snapshot today = counters.today(properties.dryRun());
        if (today.count() >= properties.maxDailyOrders()) {
            violations.add(
                    "오늘 주문 횟수 한도(%d회)에 도달했습니다. (TOSSINVEST_MAX_DAILY_ORDERS)".formatted(properties.maxDailyOrders()));
        }
        if (today.amountKrw().add(estimatedKrw).compareTo(properties.maxDailyKrw()) > 0) {
            violations.add("일일 주문 금액 한도 초과: 오늘 누적 %s원 + 이번 %s원 > 한도 %s원 (TOSSINVEST_MAX_DAILY_KRW)"
                    .formatted(
                            today.amountKrw().toPlainString(),
                            estimatedKrw.toPlainString(),
                            properties.maxDailyKrw().toPlainString()));
        }
        if (today.lastOrderAt() != null) {
            Duration since = Duration.between(today.lastOrderAt(), clock.instant());
            if (since.getSeconds() < properties.minOrderIntervalSec()) {
                violations.add("연속 주문 간격이 너무 짧습니다. %d초 후 다시 시도하세요. (TOSSINVEST_MIN_ORDER_INTERVAL_SEC)"
                        .formatted(properties.minOrderIntervalSec() - since.getSeconds()));
            }
        }
        return GuardrailResult.of(violations);
    }
}
