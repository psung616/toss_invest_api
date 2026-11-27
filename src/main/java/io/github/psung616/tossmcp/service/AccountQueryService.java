package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.OrderDetailDto;
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

    /** 매도 가능 수량 (P3-4). */
    public BigDecimal sellableQuantity(String symbol) {
        return tossApi.getSellableQuantity(symbol).sellableQuantity();
    }

    /**
     * 주문 상세 (P3-4). 미체결이면 filledQuantity=0, 체결 관련 값은 null 일 수 있다.
     *
     * @param orderAmount 금액 기반 US 시장가 매수만, 아니면 null
     */
    public record OrderDetail(
            String orderId,
            String symbol,
            String side,
            String orderType,
            String timeInForce,
            String status,
            BigDecimal price,
            BigDecimal quantity,
            BigDecimal orderAmount,
            String currency,
            String orderedAt,
            String canceledAt,
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            BigDecimal filledAmount,
            BigDecimal commission,
            BigDecimal tax,
            String filledAt,
            String settlementDate) {}

    public OrderDetail order(String orderId) {
        OrderDetailDto o = tossApi.getOrder(orderId);
        OrderDetailDto.Execution e = o.execution() == null
                ? new OrderDetailDto.Execution(BigDecimal.ZERO, null, null, null, null, null, null)
                : o.execution();
        return new OrderDetail(
                o.orderId(),
                o.symbol(),
                o.side(),
                o.orderType(),
                o.timeInForce(),
                o.status(),
                o.price(),
                o.quantity(),
                o.orderAmount(),
                o.currency(),
                o.orderedAt(),
                o.canceledAt(),
                e.filledQuantity(),
                e.averageFilledPrice(),
                e.filledAmount(),
                e.commission(),
                e.tax(),
                e.filledAt(),
                e.settlementDate());
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
