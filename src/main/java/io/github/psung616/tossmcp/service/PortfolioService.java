package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.HoldingsDto;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.Item;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.Price;
import io.github.psung616.tossmcp.client.error.TossApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * 보유 종목·평가·손익·비중 (P3-4). MCP {@code get_portfolio}·web {@code /api/portfolio} 가 공유한다.
 *
 * <p>토스 합계는 통화별(국내 KRW 합, 해외 USD 합)이라, 원화 합산은 표준 환율(USD→KRW)로 직접 계산한다. 해외 종목이 없으면 환율을 조회하지 않는다. 환율 조회가 실패하면
 * 통화별 합계는 그대로 주고 원화 합산·비중은 null 로 둔다(조회 전체를 실패시키지 않음). 원화 환산값은 원 단위 반올림, 비중은 소수 4자리(0.1234 = 12.34%).
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    /** 통화별 합계. 해외 종목이 없으면 {@link Portfolio#usd()} 가 null. */
    public record CurrencyTotals(BigDecimal purchase, BigDecimal value, BigDecimal profit, BigDecimal dailyProfit) {}

    /**
     * 보유 종목 한 줄 (거래 통화 기준). {@code valueKrw}·{@code weight} 는 원화 합산을 못 하면 null.
     *
     * @param profitRate 소수(0.1077 = 10.77%)
     */
    public record Holding(
            String symbol,
            String name,
            String country,
            String currency,
            BigDecimal quantity,
            BigDecimal lastPrice,
            BigDecimal averagePrice,
            BigDecimal purchase,
            BigDecimal value,
            BigDecimal profit,
            BigDecimal profitRate,
            BigDecimal dailyProfit,
            BigDecimal dailyProfitRate,
            BigDecimal valueKrw,
            BigDecimal weight) {}

    /**
     * @param usdKrwRate 1 USD 당 KRW (해외 종목이 없거나 조회 실패면 null)
     * @param totalValueKrw 원화 합산 평가금액 (합산 불가면 null)
     * @param profitRate 토스가 계산한 전체 수익률(소수)
     */
    public record Portfolio(
            List<Holding> holdings,
            CurrencyTotals krw,
            CurrencyTotals usd,
            BigDecimal usdKrwRate,
            BigDecimal totalValueKrw,
            BigDecimal totalProfitKrw,
            BigDecimal profitRate,
            BigDecimal dailyProfitRate) {

        public boolean isEmpty() {
            return holdings.isEmpty();
        }

        /** 해외 종목이 있는데 환율을 못 받아 원화 합산을 못 한 경우. */
        public boolean combinedUnavailable() {
            return usd != null && usdKrwRate == null;
        }
    }

    private final TossApi tossApi;

    public PortfolioService(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    public Portfolio portfolio() {
        HoldingsDto h = tossApi.getHoldings();
        List<Item> items = h.items() == null ? List.of() : h.items();
        boolean hasUsd = items.stream().anyMatch(i -> "USD".equals(i.currency()))
                || h.marketValue().amount().usd() != null;

        CurrencyTotals krw = totals(h, Price::krw);
        CurrencyTotals usd = hasUsd ? totals(h, Price::usd) : null;
        BigDecimal rate = hasUsd ? usdKrwRate() : null;
        boolean combinable = !hasUsd || rate != null;

        BigDecimal totalValueKrw = combinable ? toKrw(krw.value(), usd == null ? null : usd.value(), rate) : null;
        BigDecimal totalProfitKrw = combinable ? toKrw(krw.profit(), usd == null ? null : usd.profit(), rate) : null;

        List<Holding> holdings = items.stream()
                .map(i -> holding(i, combinable ? rate : null, combinable, totalValueKrw))
                .toList();
        return new Portfolio(
                holdings,
                krw,
                usd,
                rate,
                totalValueKrw,
                totalProfitKrw,
                h.profitLoss().rate(),
                h.dailyProfitLoss().rate());
    }

    private BigDecimal usdKrwRate() {
        try {
            return tossApi.getExchangeRate("USD", "KRW").rate();
        } catch (TossApiException | ResourceAccessException e) {
            log.warn("환율 조회 실패 — 원화 합산·비중 없이 보여 줌: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static Holding holding(Item i, BigDecimal rate, boolean combinable, BigDecimal totalValueKrw) {
        BigDecimal value = i.marketValue().amount();
        BigDecimal valueKrw = null;
        if (combinable) {
            valueKrw = "USD".equals(i.currency()) ? value.multiply(rate).setScale(0, RoundingMode.HALF_UP) : value;
        }
        BigDecimal weight = valueKrw == null || totalValueKrw == null || totalValueKrw.signum() == 0
                ? null
                : valueKrw.divide(totalValueKrw, 4, RoundingMode.HALF_UP);
        return new Holding(
                i.symbol(),
                i.name(),
                i.marketCountry(),
                i.currency(),
                i.quantity(),
                i.lastPrice(),
                i.averagePurchasePrice(),
                i.marketValue().purchaseAmount(),
                value,
                i.profitLoss().amount(),
                i.profitLoss().rate(),
                i.dailyProfitLoss().amount(),
                i.dailyProfitLoss().rate(),
                valueKrw,
                weight);
    }

    private static CurrencyTotals totals(HoldingsDto h, java.util.function.Function<Price, BigDecimal> pick) {
        return new CurrencyTotals(
                zeroIfNull(pick.apply(h.totalPurchaseAmount())),
                zeroIfNull(pick.apply(h.marketValue().amount())),
                zeroIfNull(pick.apply(h.profitLoss().amount())),
                zeroIfNull(pick.apply(h.dailyProfitLoss().amount())));
    }

    private static BigDecimal toKrw(BigDecimal krw, BigDecimal usd, BigDecimal rate) {
        if (usd == null) {
            return krw;
        }
        return krw.add(usd.multiply(rate)).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
