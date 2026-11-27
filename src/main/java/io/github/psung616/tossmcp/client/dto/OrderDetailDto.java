package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * GET /api/v1/orders/{orderId} 의 result ({@code Order}). status: PENDING·PENDING_CANCEL·PENDING_REPLACE·PARTIAL_FILLED·FILLED·CANCELED·
 * REJECTED·CANCEL_REJECTED·REPLACE_REJECTED·REPLACED (모르는 값도 올 수 있음). 금액은 거래 통화 기준.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDetailDto(
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
        Execution execution) {

    /** 체결 결과. 미체결이면 filledQuantity=0, 나머지는 null 일 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Execution(
            BigDecimal filledQuantity,
            BigDecimal averageFilledPrice,
            BigDecimal filledAmount,
            BigDecimal commission,
            BigDecimal tax,
            String filledAt,
            String settlementDate) {}
}
