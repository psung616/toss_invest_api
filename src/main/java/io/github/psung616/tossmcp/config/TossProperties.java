package io.github.psung616.tossmcp.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 주문 설정. 모든 값은 application.yml 에서 환경변수로 명시 매핑된다 (ARCHITECTURE §4).
 *
 * TODO(P1-2): 기본값·환경변수 반영·클램프·외부 설정 무시 테스트
 */
@Validated
@ConfigurationProperties(prefix = "tossinvest")
public record TossProperties(
        boolean enableOrders,
        boolean dryRun,
        @NotNull @Positive BigDecimal maxOrderKrw,
        @NotNull @Positive BigDecimal maxDailyKrw,
        @Positive int maxDailyOrders,
        List<String> allowedSymbols,
        boolean allowMarketOrders,
        boolean allowSell,
        boolean allowUs,
        int minOrderIntervalSec,
        @NotNull Duration previewTtl) {

    /** 설정과 무관하게 1회 주문은 1억원 미만으로 강제한다. */
    public static final BigDecimal HARD_MAX_ORDER_KRW = new BigDecimal("99999999");

    private static final Logger log = LoggerFactory.getLogger(TossProperties.class);

    public TossProperties {
        allowedSymbols = allowedSymbols == null
                ? List.of()
                : allowedSymbols.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toUpperCase(Locale.ROOT))
                        .toList();
        if (maxOrderKrw != null && maxOrderKrw.compareTo(HARD_MAX_ORDER_KRW) > 0) {
            log.warn("TOSSINVEST_MAX_ORDER_KRW 가 하드 상한을 넘어 {}원으로 조정됩니다.", HARD_MAX_ORDER_KRW);
            maxOrderKrw = HARD_MAX_ORDER_KRW;
        }
    }

    /** 허용 목록이 비어 있으면 주문 불가 (조회는 가능). */
    public boolean isSymbolAllowed(String symbol) {
        return allowedSymbols.contains(symbol.toUpperCase(Locale.ROOT));
    }
}
