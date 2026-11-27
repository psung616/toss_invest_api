package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/** 계좌 조회 (P9-3). MCP {@code get_buying_power}·{@code list_orders} 와 web {@code /api/buying-power}·{@code /api/orders} 가 공유한다. */
@Service
public class AccountQueryService {

    public static final String DEFAULT_CURRENCY = "KRW";
    public static final String DEFAULT_ORDER_STATUS = "OPEN";

    public record BuyingPower(String currency, BigDecimal cash) {}

    /** price·quantity·orderedAt 는 없을 수 있다(시장가 등). */
    public record OrderItem(
            String orderId,
            String symbol,
            String side,
            String orderType,
            String status,
            BigDecimal price,
            BigDecimal quantity,
            String currency,
            String orderedAt) {}

    private final TossApi tossApi;

    public AccountQueryService(TossApi tossApi) {
        this.tossApi = tossApi;
    }

    /** @param currency KRW·USD, 비어 있으면 KRW */
    public BuyingPower buyingPower(String currency) {
        BuyingPowerDto bp = tossApi.getBuyingPower(orDefault(currency, DEFAULT_CURRENCY));
        return new BuyingPower(bp.currency(), bp.cashBuyingPower());
    }

    /** @param status OPEN·CLOSED, 비어 있으면 OPEN. 주문이 없으면 빈 목록 */
    public List<OrderItem> orders(String status) {
        OrderListDto list = tossApi.listOrders(orDefault(status, DEFAULT_ORDER_STATUS));
        if (list.orders() == null) {
            return List.of();
        }
        return list.orders().stream()
                .map(o -> new OrderItem(
                        o.orderId(),
                        o.symbol(),
                        o.side(),
                        o.orderType(),
                        o.status(),
                        o.price(),
                        o.quantity(),
                        o.currency(),
                        o.orderedAt()))
                .toList();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
