package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

/**
 * GET /api/v1/market-calendar/KR 의 result. 통합 모드(KRX+NXT), 전일·당일·익일 3영업일. 시각은 KST 오프셋 포함.
 * 휴장이면 integrated 또는 각 세션이 null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketCalendarDto(BusinessDay today, BusinessDay previousBusinessDay, BusinessDay nextBusinessDay) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BusinessDay(String date, Sessions integrated) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sessions(Session preMarket, Session regularMarket, Session afterMarket) {

        public Stream<Session> all() {
            return Stream.of(preMarket, regularMarket, afterMarket).filter(s -> s != null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(OffsetDateTime startTime, OffsetDateTime endTime) {}
}
