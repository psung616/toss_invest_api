package io.github.psung616.tossmcp.order;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.dto.MarketCalendarDto;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto.BusinessDay;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto.Session;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto.Sessions;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 공식 스펙 예시 형식을 본뜬 가짜 달력. */
class TossMarketHoursTest {

    private static Session session(String start, String end) {
        return new Session(OffsetDateTime.parse(start), OffsetDateTime.parse(end));
    }

    private static Sessions day(String date) {
        return new Sessions(
                session(date + "T08:00:00+09:00", date + "T09:00:00+09:00"),
                session(date + "T09:00:00+09:00", date + "T15:30:00+09:00"),
                session(date + "T15:30:00+09:00", date + "T20:00:00+09:00"));
    }

    private final MarketCalendarDto calendar = new MarketCalendarDto(
            new BusinessDay("2026-11-24", day("2026-11-24")),
            new BusinessDay("2026-11-23", day("2026-11-23")),
            new BusinessDay("2026-11-25", day("2026-11-25")));

    private static Optional<String> at(MarketCalendarDto c, String instant) {
        return TossMarketHours.evaluate(c, OffsetDateTime.parse(instant).toInstant());
    }

    @Test
    void openDuringRegularAndExtendedSessions() {
        assertThat(at(calendar, "2026-11-24T10:00:00+09:00")).isEmpty();
        assertThat(at(calendar, "2026-11-24T08:30:00+09:00")).isEmpty();
        assertThat(at(calendar, "2026-11-24T19:59:00+09:00")).isEmpty();
    }

    @Test
    void closedBeforeOpenShowsTodayStart() {
        assertThat(at(calendar, "2026-11-24T07:00:00+09:00"))
                .hasValueSatisfying(r -> assertThat(r).contains("2026-11-24 08:00"));
    }

    @Test
    void closedAfterCloseShowsNextBusinessDay() {
        assertThat(at(calendar, "2026-11-24T20:00:00+09:00"))
                .hasValueSatisfying(r -> assertThat(r).contains("2026-11-25 08:00"));
    }

    @Test
    void holidayIsClosed() {
        MarketCalendarDto holiday = new MarketCalendarDto(
                new BusinessDay("2026-11-24", null), calendar.previousBusinessDay(), calendar.nextBusinessDay());

        assertThat(at(holiday, "2026-11-24T10:00:00+09:00")).isPresent();
    }

    @Test
    void usdIsNotCheckedHere() {
        TossMarketHours hours = new TossMarketHours(null);

        assertThat(hours.closedReason("USD", Instant.now())).isEmpty();
    }

    @Test
    void calendarFailureFailsClosed() {
        TossMarketHours hours = new TossMarketHours(null); // getMarketCalendarKr 호출 시 NPE → 거부

        assertThat(hours.closedReason("KRW", Instant.now()))
                .hasValueSatisfying(r -> assertThat(r).contains("확인할 수 없어"));
    }
}
