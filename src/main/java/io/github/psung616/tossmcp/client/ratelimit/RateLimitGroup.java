package io.github.psung616.tossmcp.client.ratelimit;

/**
 * 토스증권 레이트리밋 그룹과 초당 요청 한도 (클라이언트 × 그룹).
 *
 * <p>출처: overview.md "Rate Limits" 표, 엔드포인트별 그룹은 openapi.json 각 operation 의 {@code Rate Limits Group} 표기.
 * 전체 매핑은 docs/design/TOSS_API_NOTES.md "스펙 확인 결과" 참고. 한도는 예고 없이 바뀔 수 있어 응답 헤더 {@code X-RateLimit-Limit} 으로
 * 낮춰 적용한다({@link RateLimiter#observeLimit}).
 */
public enum RateLimitGroup {
    AUTH(5),
    ACCOUNT(1),
    ASSET(5),
    TRANSACTION(5),
    STOCK(5),
    STOCK_ALL(1),
    STOCK_TRADING_TREND(10),
    MARKET_INFO(3),
    MARKET_DATA(15),
    MARKET_DATA_CHART(20),
    RANKING(5),
    MARKET_INDICATOR_PRICE(10),
    MARKET_INDICATOR(10),
    MARKET_INDICATOR_CHART(5),
    SECTOR(5),
    SECTOR_RANKING(5),
    /** 09:00~09:10 KST 에도 10 유지. */
    ORDER(10),
    ORDER_HISTORY(5),
    /** 09:00~09:10 KST 에는 3. */
    ORDER_INFO(6, 3),
    CONDITIONAL_ORDER(5),
    CONDITIONAL_ORDER_HISTORY(10);

    private final int limit;
    private final int peakLimit;

    RateLimitGroup(int limit) {
        this(limit, limit);
    }

    RateLimitGroup(int limit, int peakLimit) {
        this.limit = limit;
        this.peakLimit = peakLimit;
    }

    /** 평시 초당 한도. */
    public int limit() {
        return limit;
    }

    /** 피크 시간(09:00~09:10 KST) 초당 한도. */
    public int peakLimit() {
        return peakLimit;
    }
}
