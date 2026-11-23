package io.github.psung616.tossmcp.order;

import java.time.Instant;
import java.util.Optional;

/** 장 운영 시간 판단. 주문 불가면 사유 문장을 돌려준다. */
public interface MarketHours {

    /**
     * @param currency 종목 통화 (KRW = 국내)
     * @return 주문할 수 없으면 사유, 가능하면 empty
     */
    Optional<String> closedReason(String currency, Instant now);
}
