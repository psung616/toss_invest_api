package io.github.psung616.tossmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderDetailDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto.OrderSummary;
import io.github.psung616.tossmcp.client.dto.SellableQuantityDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** P9-3: MCP 도구 문구 고정 (서비스 추출 전후로 같아야 한다). 모든 값은 지어낸 가짜 값. */
class AccountToolsTest {

    private final TossApi tossApi = mock(TossApi.class);
    private final AccountTools tools = ToolFixtures.accountTools(tossApi);

    @Test
    void buyingPowerDefaultsToKrw() {
        when(tossApi.getBuyingPower("KRW")).thenReturn(new BuyingPowerDto("KRW", new BigDecimal("1234567")));

        assertThat(tools.getBuyingPower(null)).isEqualTo("매수 가능 금액: 1234567 KRW");
        assertThat(tools.getBuyingPower(" ")).isEqualTo("매수 가능 금액: 1234567 KRW");
    }

    @Test
    void buyingPowerInUsd() {
        when(tossApi.getBuyingPower("USD")).thenReturn(new BuyingPowerDto("USD", new BigDecimal("100.50")));

        assertThat(tools.getBuyingPower("USD")).isEqualTo("매수 가능 금액: 100.50 USD");
    }

    @Test
    void ordersDefaultToOpenAndFormatEachLine() {
        when(tossApi.listOrders("OPEN"))
                .thenReturn(new OrderListDto(
                        List.of(
                                new OrderSummary(
                                        "ord-1",
                                        "005930",
                                        "BUY",
                                        "LIMIT",
                                        "OPEN",
                                        new BigDecimal("70000"),
                                        new BigDecimal("3"),
                                        "KRW",
                                        "2026-11-27T09:10:00+09:00"),
                                new OrderSummary("ord-2", "NVDA", "SELL", "MARKET", "OPEN", null, null, "USD", null)),
                        null,
                        false));

        assertThat(tools.listOrders(null))
                .isEqualTo("ord-1 005930 BUY LIMIT 수량 3 가격 70000 KRW (OPEN)\n"
                        + "ord-2 NVDA SELL MARKET 수량 - 가격 - USD (OPEN)");
    }

    @Test
    void noOrders() {
        when(tossApi.listOrders("CLOSED")).thenReturn(new OrderListDto(List.of(), null, false));
        when(tossApi.listOrders("OPEN")).thenReturn(new OrderListDto(null, null, false));

        assertThat(tools.listOrders("CLOSED")).isEqualTo("주문이 없습니다.");
        assertThat(tools.listOrders("OPEN")).isEqualTo("주문이 없습니다.");
    }

    // ---- P3-4

    @Test
    void portfolioText() {
        when(tossApi.getHoldings()).thenReturn(PortfolioServiceTestData.mixed());
        when(tossApi.getExchangeRate("USD", "KRW")).thenReturn(PortfolioServiceTestData.rate("1400.50"));

        assertThat(tools.getPortfolio())
                .isEqualTo("보유 종목 2개 (표준 환율 1 USD = 1400.50 KRW 로 원화 합산)\n"
                        + "합계 평가금액: 878760 KRW (국내 710000 KRW + 해외 120.50 USD)\n"
                        + "합계 손익: +38710 KRW\n"
                        + "수익률: +2.50%, 오늘: -0.12%\n"
                        + "- 005930 가짜005930 10주 | 평가 710000 KRW | 손익 +10000 KRW (+1.42%) | 비중 80.80%\n"
                        + "- NVDA 가짜NVDA 1주 | 평가 120.50 USD | 손익 +20.50 USD (+20.50%) | 비중 19.20%");
    }

    @Test
    void emptyPortfolio() {
        when(tossApi.getHoldings()).thenReturn(PortfolioServiceTestData.empty());

        assertThat(tools.getPortfolio()).isEqualTo("보유 종목이 없습니다.");
    }

    @Test
    void sellableQuantity() {
        when(tossApi.getSellableQuantity("005930")).thenReturn(new SellableQuantityDto(new BigDecimal("3")));

        assertThat(tools.getSellableQuantity(" 005930 ")).isEqualTo("005930 매도 가능 수량: 3");
        assertThat(tools.getSellableQuantity(" ")).startsWith("[요청 오류]");
    }

    @Test
    void orderDetail() {
        when(tossApi.getOrder("ord-9"))
                .thenReturn(new OrderDetailDto(
                        "ord-9",
                        "NVDA",
                        "BUY",
                        "LIMIT",
                        "DAY",
                        "PARTIAL_FILLED",
                        new BigDecimal("100.00"),
                        new BigDecimal("5"),
                        null,
                        "USD",
                        "2026-11-27T23:30:00+09:00",
                        null,
                        new OrderDetailDto.Execution(
                                new BigDecimal("2"),
                                new BigDecimal("99.50"),
                                new BigDecimal("199.00"),
                                new BigDecimal("0.10"),
                                null,
                                "2026-11-27T23:31:00+09:00",
                                null)));

        assertThat(tools.getOrder("ord-9"))
                .isEqualTo(String.join(
                        "\n",
                        "ord-9 NVDA BUY LIMIT(DAY) PARTIAL_FILLED",
                        "주문: 수량 5 가격 100.00 USD",
                        "주문 시각: 2026-11-27T23:30:00+09:00",
                        "체결: 수량 2 평균가 99.50 금액 199.00 수수료 0.10 세금 -",
                        "최근 체결 시각: 2026-11-27T23:31:00+09:00, 결제일: -"));
    }

    @Test
    void tossErrorBecomesUserMessage() {
        when(tossApi.getBuyingPower("KRW"))
                .thenThrow(new TossApiException(401, "invalid_client", "Client authentication failed", "req-7"));

        assertThat(tools.getBuyingPower("KRW")).startsWith("[토스증권 API 오류] ").contains("req-7");
    }
}
