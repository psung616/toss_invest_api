package io.github.psung616.tossmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
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
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.service.PortfolioService.Holding;
import io.github.psung616.tossmcp.service.PortfolioService.Portfolio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P3-4: 혼합 포트폴리오 원화 환산 정확도, 국내 전용, 빈 계좌, 환율 실패. 값은 모두 지어낸 것. */
class PortfolioServiceTest {

    private final TossApi tossApi = mock(TossApi.class);
    private final PortfolioService service = new PortfolioService(tossApi);

    static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    static Price price(String krw, String usd) {
        return new Price(d(krw), d(usd));
    }

    static Item item(String symbol, String currency, String qty, String value, String profit, String rate) {
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

    static HoldingsDto holdings(Price purchase, Price value, Price profit, List<Item> items) {
        return new HoldingsDto(
                purchase,
                new OverviewMarketValue(value, value),
                new OverviewProfitLoss(profit, profit, d("0.0250"), d("0.0230")),
                new OverviewDailyProfitLoss(price("0", null), d("-0.0012")),
                items);
    }

    private void rate(String rate) {
        when(tossApi.getExchangeRate("USD", "KRW"))
                .thenReturn(new ExchangeRateDto("USD", "KRW", d(rate), d(rate), d("0"), "EQUAL", null, null));
    }

    @Test
    void mixedPortfolioConvertsUsdAtStandardRate() {
        when(tossApi.getHoldings())
                .thenReturn(holdings(
                        price("700000", "100.00"),
                        price("710000", "120.50"),
                        price("10000", "20.50"),
                        List.of(
                                item("005930", "KRW", "10", "710000", "10000", "0.0142"),
                                item("NVDA", "USD", "1", "120.50", "20.50", "0.2050"))));
        rate("1400.50");

        Portfolio p = service.portfolio();

        // 120.50 * 1400.50 = 168760.25 → 168760
        assertThat(p.usdKrwRate()).isEqualByComparingTo("1400.50");
        assertThat(p.totalValueKrw()).isEqualByComparingTo("878760");
        // 20.50 * 1400.50 = 28710.25 → 10000 + 28710.25 = 38710.25 → 38710
        assertThat(p.totalProfitKrw()).isEqualByComparingTo("38710");
        Holding nvda = p.holdings().get(1);
        assertThat(nvda.valueKrw()).isEqualByComparingTo("168760");
        // 비중: 710000/878760 = 0.80795 → 0.8080, 168760/878760 = 0.19204 → 0.1920
        assertThat(p.holdings().get(0).weight()).isEqualByComparingTo("0.8080");
        assertThat(nvda.weight()).isEqualByComparingTo("0.1920");
        assertThat(p.krw().value()).isEqualByComparingTo("710000");
        assertThat(p.usd().value()).isEqualByComparingTo("120.50");
        assertThat(p.combinedUnavailable()).isFalse();
    }

    @Test
    void koreanOnlyPortfolioDoesNotFetchExchangeRate() {
        when(tossApi.getHoldings())
                .thenReturn(holdings(
                        price("300000", null),
                        price("330000", null),
                        price("30000", null),
                        List.of(
                                item("005930", "KRW", "2", "220000", "20000", "0.1"),
                                item("000660", "KRW", "1", "110000", "10000", "0.1"))));

        Portfolio p = service.portfolio();

        verify(tossApi, never()).getExchangeRate(anyString(), anyString());
        assertThat(p.usd()).isNull();
        assertThat(p.totalValueKrw()).isEqualByComparingTo("330000");
        assertThat(p.holdings().get(0).weight()).isEqualByComparingTo("0.6667");
        assertThat(p.holdings().get(1).weight()).isEqualByComparingTo("0.3333");
    }

    @Test
    void emptyAccount() {
        when(tossApi.getHoldings())
                .thenReturn(holdings(price("0", null), price("0", null), price("0", null), List.of()));

        Portfolio p = service.portfolio();

        assertThat(p.isEmpty()).isTrue();
        assertThat(p.totalValueKrw()).isEqualByComparingTo("0");
        verify(tossApi, never()).getExchangeRate(anyString(), anyString());
    }

    @Test
    void exchangeRateFailureKeepsPerCurrencyTotals() {
        when(tossApi.getHoldings())
                .thenReturn(holdings(
                        price("700000", "100.00"),
                        price("710000", "120.50"),
                        price("10000", "20.50"),
                        List.of(
                                item("005930", "KRW", "10", "710000", "10000", "0.0142"),
                                item("NVDA", "USD", "1", "120.50", "20.50", "0.2050"))));
        when(tossApi.getExchangeRate("USD", "KRW")).thenThrow(new TossApiException(503, "maintenance", "x", null));

        Portfolio p = service.portfolio();

        assertThat(p.combinedUnavailable()).isTrue();
        assertThat(p.totalValueKrw()).isNull();
        assertThat(p.holdings()).allSatisfy(h -> assertThat(h.weight()).isNull());
        assertThat(p.usd().profit()).isEqualByComparingTo("20.50");
    }
}
