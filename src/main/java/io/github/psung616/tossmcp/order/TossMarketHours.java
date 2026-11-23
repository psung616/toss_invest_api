package io.github.psung616.tossmcp.order;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto.Session;
import io.github.psung616.tossmcp.config.AppConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 국내: GET /api/v1/market-calendar/KR 의 통합(KRX+NXT) 세션(프리·정규·애프터) 중 하나에 들어 있을 때만 주문 가능.
 * 달력은 KST 날짜별로 한 번만 조회한다 (MARKET_INFO 그룹 초당 3회). 조회에 실패하면 안전하게 거부한다.
 *
 * TODO: 미국 주식(allow-us) 지원 시 market-calendar/US 반영
 */
@Component
public class TossMarketHours implements MarketHours {

    private static final Logger log = LoggerFactory.getLogger(TossMarketHours.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final TossApi tossApi;
    private LocalDate cachedDate;
    private MarketCalendarDto cached;

    public TossMarketHours(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    @Override
    public Optional<String> closedReason(String currency, Instant now) {
        if (!"KRW".equals(currency)) {
            return Optional.empty(); // 미국 주식은 GuardrailService 의 allow-us 검사에서 따로 막는다
        }
        MarketCalendarDto calendar;
        try {
            calendar = calendar(now);
        } catch (RuntimeException e) {
            log.warn("Market calendar unavailable: {}", e.getMessage());
            return Optional.of("장 운영 정보를 확인할 수 없어 주문을 거부합니다. 잠시 후 다시 시도하세요.");
        }
        return evaluate(calendar, now);
    }

    static Optional<String> evaluate(MarketCalendarDto calendar, Instant now) {
        MarketCalendarDto.BusinessDay today = calendar.today();
        if (today != null && today.integrated() != null) {
            boolean open = today.integrated()
                    .all()
                    .anyMatch(s -> !now.isBefore(s.startTime().toInstant())
                            && now.isBefore(s.endTime().toInstant()));
            if (open) {
                return Optional.empty();
            }
        }
        return Optional.of("지금은 국내 주식 주문 가능 시간이 아닙니다." + nextOpenHint(calendar, now));
    }

    private static String nextOpenHint(MarketCalendarDto calendar, Instant now) {
        for (MarketCalendarDto.BusinessDay day :
                new MarketCalendarDto.BusinessDay[] {calendar.today(), calendar.nextBusinessDay()}) {
            if (day == null || day.integrated() == null) {
                continue;
            }
            Optional<Session> next = day.integrated()
                    .all()
                    .filter(s -> s.startTime().toInstant().isAfter(now))
                    .findFirst();
            if (next.isPresent()) {
                return " 다음 주문 가능 시각: " + day.date() + " "
                        + next.get()
                                .startTime()
                                .atZoneSameInstant(AppConfig.KST)
                                .format(HM) + " (KST)";
            }
        }
        return "";
    }

    private synchronized MarketCalendarDto calendar(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, AppConfig.KST);
        if (cached == null || !today.equals(cachedDate)) {
            cached = tossApi.getMarketCalendarKr();
            cachedDate = today;
        }
        return cached;
    }
}
