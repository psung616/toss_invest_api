package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/orders 의 result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderListDto(List<OrderSummary> orders, String nextCursor, boolean hasNext) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderSummary(
            String orderId,
            String symbol,
            String side,
            String orderType,
            String status,
            BigDecimal price,
            BigDecimal quantity,
            String currency,
            String orderedAt) {}
}
