package io.github.psung616.tossmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto.OrderSummary;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.service.AccountQueryService.BuyingPower;
import io.github.psung616.tossmcp.service.AccountQueryService.OrderItem;
import io.github.psung616.tossmcp.service.QuoteService.Quote;
import io.github.psung616.tossmcp.service.StockSearchService.StockMatch;
import io.github.psung616.tossmcp.stock.StockEntry;
import io.github.psung616.tossmcp.stock.StockMasterCache;
import io.github.psung616.tossmcp.stock.StockSearch;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** P9-3 조회 서비스: 기본값·변환. {@code TossApi} 는 가짜, 값은 모두 지어낸 것. */
class QueryServicesTest {

    private final TossApi tossApi = mock(TossApi.class);

    @Test
    void quotesKeepDecimalAndTimestamp() {
        when(tossApi.getPrices("005930"))
                .thenReturn(
                        List.of(new PriceDto("005930", "2026-11-27T10:00:00+09:00", new BigDecimal("71000.0"), "KRW")));

        assertThat(new QuoteService(tossApi).quotes("005930"))
                .containsExactly(new Quote("005930", new BigDecimal("71000.0"), "KRW", "2026-11-27T10:00:00+09:00"));
    }

    @Test
    void buyingPowerDefaultsToKrw() {
        when(tossApi.getBuyingPower("KRW")).thenReturn(new BuyingPowerDto("KRW", new BigDecimal("500")));
        AccountQueryService service = new AccountQueryService(tossApi);

        assertThat(service.buyingPower(null)).isEqualTo(new BuyingPower("KRW", new BigDecimal("500")));
        assertThat(service.buyingPower("")).isEqualTo(new BuyingPower("KRW", new BigDecimal("500")));
    }

    @Test
    void buyingPowerPassesCurrencyThrough() {
        when(tossApi.getBuyingPower("USD")).thenReturn(new BuyingPowerDto("USD", new BigDecimal("1.25")));

        assertThat(new AccountQueryService(tossApi).buyingPower("USD").cash()).isEqualByComparingTo("1.25");
        verify(tossApi).getBuyingPower("USD");
    }

    @Test
    void ordersDefaultToOpenAndMapAllFields() {
        OrderSummary summary = new OrderSummary(
                "ord-1",
                "005930",
                "BUY",
                "LIMIT",
                "OPEN",
                new BigDecimal("70000"),
                new BigDecimal("3"),
                "KRW",
                "2026-11-27T09:10:00+09:00");
        when(tossApi.listOrders("OPEN")).thenReturn(new OrderListDto(List.of(summary), null, false));

        assertThat(new AccountQueryService(tossApi).orders(" "))
                .containsExactly(new OrderItem(
                        "ord-1",
                        "005930",
                        "BUY",
                        "LIMIT",
                        "OPEN",
                        new BigDecimal("70000"),
                        new BigDecimal("3"),
                        "KRW",
                        "2026-11-27T09:10:00+09:00"));
    }

    @Test
    void missingOrderListIsEmpty() {
        when(tossApi.listOrders("CLOSED")).thenReturn(new OrderListDto(null, null, false));

        assertThat(new AccountQueryService(tossApi).orders("CLOSED")).isEmpty();
    }

    @Test
    void searchMapsEntriesAndDefaultsLimitToTen() {
        StockMasterCache cache = mock(StockMasterCache.class);
        List<StockEntry> many = IntStream.range(0, 15)
                .mapToObj(i -> new StockEntry("9000%02d".formatted(i), "가짜종목" + i, "KOSDAQ", "STOCK", true))
                .toList();
        when(cache.search()).thenReturn(new StockSearch(many));
        StockSearchService service = new StockSearchService(cache);

        List<StockMatch> found = service.search("가짜종목", null, null);
        assertThat(found).hasSize(StockSearchService.DEFAULT_LIMIT);
        assertThat(found.getFirst().market()).isEqualTo("KOSDAQ");
        assertThat(service.search("가짜종목", null, 3)).hasSize(3);
        assertThat(service.search("가짜종목", "US", null)).isEmpty();
    }
}
