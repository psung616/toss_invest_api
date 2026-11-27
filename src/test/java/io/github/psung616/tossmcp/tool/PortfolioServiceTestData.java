package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.client.dto.ExchangeRateDto;
import io.github.psung616.tossmcp.client.dto.HoldingsDto;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.Cost;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.DailyProfitLoss;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.Item;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.MarketValue;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.OverviewDailyProfitLoss;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.OverviewMarketValue;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.OverviewProfitLoss;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.Price;
import io.github.psung616.tossmcp.client.dto.HoldingsDto.ProfitLoss;
import java.math.BigDecimal;
import java.util.List;

/** 지어낸 보유 종목 데이터 (도구·web 테스트 공용). */
final class PortfolioServiceTestData {

    private PortfolioServiceTestData() {}

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static Item item(String symbol, String currency, String qty, String value, String profit, String rate) {
        return new Item(
                symbol,
                "가짜" + symbol,
                "KRW".equals(currency) ? "KR" : "US",
                currency,
                d(qty),
                d("1"),
                d("1"),
                new MarketValue(d("0"), d(value), d(value)),
                new ProfitLoss(d(profit), d(profit), d(rate), d(rate)),
                new DailyProfitLoss(d("0"), d("0")),
                new Cost(d("0"), null));
    }

    private static HoldingsDto holdings(Price purchase, Price value, Price profit, List<Item> items) {
        return new HoldingsDto(
                purchase,
                new OverviewMarketValue(value, value),
                new OverviewProfitLoss(profit, profit, d("0.0250"), d("0.0230")),
                new OverviewDailyProfitLoss(new Price(d("0"), null), d("-0.0012")),
                items);
    }

    /** 국내 710,000 KRW + 해외 120.50 USD. */
    static HoldingsDto mixed() {
        return holdings(
                new Price(d("700000"), d("100.00")),
                new Price(d("710000"), d("120.50")),
                new Price(d("10000"), d("20.50")),
                List.of(
                        item("005930", "KRW", "10", "710000", "10000", "0.0142"),
                        item("NVDA", "USD", "1", "120.50", "20.50", "0.2050")));
    }

    static HoldingsDto empty() {
        return holdings(new Price(d("0"), null), new Price(d("0"), null), new Price(d("0"), null), List.of());
    }

    static ExchangeRateDto rate(String rate) {
        return new ExchangeRateDto("USD", "KRW", d(rate), d(rate), d("0"), "EQUAL", null, null);
    }
}
