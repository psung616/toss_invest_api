package io.github.psung616.tossmcp.order;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * 채팅에서 들어온 주문 의도. 생성 시점에 형식 검증과 정규화를 끝낸다.
 */
public record OrderCommand(String symbol, OrderSide side, OrderType type, long quantity, BigDecimal limitPrice) {

    public OrderCommand {
        Objects.requireNonNull(symbol, "종목 코드(symbol)는 필수입니다.");
        Objects.requireNonNull(side, "매수/매도 구분(side)은 필수입니다.");
        Objects.requireNonNull(type, "주문 유형(orderType)은 필수입니다.");

        symbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("종목 코드는 비어 있을 수 없습니다.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        if (type == OrderType.LIMIT && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new IllegalArgumentException("지정가 주문은 0 보다 큰 가격(limitPrice)이 필요합니다.");
        }
        if (type == OrderType.MARKET) {
            limitPrice = null;
        }
    }
}
