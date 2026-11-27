package io.github.psung616.tossmcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto.OrderSummary;
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

    @Test
    void tossErrorBecomesUserMessage() {
        when(tossApi.getBuyingPower("KRW"))
                .thenThrow(new TossApiException(401, "invalid_client", "Client authentication failed", "req-7"));

        assertThat(tools.getBuyingPower("KRW")).startsWith("[토스증권 API 오류] ").contains("req-7");
    }
}
