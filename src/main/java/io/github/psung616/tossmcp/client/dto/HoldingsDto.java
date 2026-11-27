package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/holdings 의 result ({@code HoldingsOverview}, openapi v1.2.28). 국내(KR)·미국(US) 주식만.
 *
 * <p>합계의 {@link Price} 는 <b>통화별 합계</b>다: {@code krw} = KRW 로 거래되는 국내 종목 합, {@code usd} = USD 로 거래되는 해외 종목 합(없으면 null).
 * 서로 환산된 값이 아니다. 비율({@code rate})은 소수(0.1516 = 15.16%)이며 전체 비율은 토스가 환율을 반영해 계산한 값이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HoldingsDto(
        Price totalPurchaseAmount,
        OverviewMarketValue marketValue,
        OverviewProfitLoss profitLoss,
        OverviewDailyProfitLoss dailyProfitLoss,
        List<Item> items) {

    /** 통화별 합계. krw 필수, usd 는 해외 종목이 없으면 null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(BigDecimal krw, BigDecimal usd) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverviewMarketValue(Price amount, Price amountAfterCost) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverviewProfitLoss(Price amount, Price amountAfterCost, BigDecimal rate, BigDecimal rateAfterCost) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OverviewDailyProfitLoss(Price amount, BigDecimal rate) {}

    /** 보유 종목 한 줄. 금액·가격은 거래 통화({@code currency}) 기준. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String symbol,
            String name,
            String marketCountry,
            String currency,
            BigDecimal quantity,
            BigDecimal lastPrice,
            BigDecimal averagePurchasePrice,
            MarketValue marketValue,
            ProfitLoss profitLoss,
            DailyProfitLoss dailyProfitLoss,
            Cost cost) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketValue(BigDecimal purchaseAmount, BigDecimal amount, BigDecimal amountAfterCost) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProfitLoss(
            BigDecimal amount, BigDecimal amountAfterCost, BigDecimal rate, BigDecimal rateAfterCost) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyProfitLoss(BigDecimal amount, BigDecimal rate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cost(BigDecimal commission, BigDecimal tax) {}
}
