package io.github.psung616.tossmcp.tool;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 계좌 조회 도구.
 *
 * TODO(P3-4): get_portfolio, get_sellable_quantity, get_order, record 반환
 */
@Component
public class AccountTools {

    private final TossApi tossApi;

    public AccountTools(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    @McpTool(
            name = "get_buying_power",
            description = "주문 전에 매수 가능 현금을 확인할 때 사용합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getBuyingPower(
            @McpToolParam(description = "통화: KRW(국내) 또는 USD(미국). 기본 KRW", required = false) String currency) {
        return ToolErrors.handle(() -> {
            BuyingPowerDto bp = tossApi.getBuyingPower(currency == null || currency.isBlank() ? "KRW" : currency);
            return "매수 가능 금액: %s %s".formatted(bp.cashBuyingPower().toPlainString(), bp.currency());
        });
    }

    @McpTool(
            name = "list_orders",
            description = "미체결(OPEN) 또는 완료(CLOSED) 주문 목록을 확인할 때 사용합니다.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listOrders(
            @McpToolParam(description = "OPEN(미체결) 또는 CLOSED(체결·취소 완료). 기본 OPEN", required = false) String status) {
        return ToolErrors.handle(() -> {
            OrderListDto list = tossApi.listOrders(status == null || status.isBlank() ? "OPEN" : status);
            if (list.orders() == null || list.orders().isEmpty()) {
                return "주문이 없습니다.";
            }
            return list.orders().stream()
                    .map(o -> "%s %s %s %s 수량 %s 가격 %s %s (%s)"
                            .formatted(
                                    o.orderId(),
                                    o.symbol(),
                                    o.side(),
                                    o.orderType(),
                                    o.quantity() == null ? "-" : o.quantity().toPlainString(),
                                    o.price() == null ? "-" : o.price().toPlainString(),
                                    o.currency(),
                                    o.status()))
                    .collect(Collectors.joining("\n"));
        });
    }
}
