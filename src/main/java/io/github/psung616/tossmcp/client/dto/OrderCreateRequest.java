package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/v1/orders 바디 (수량 기반 주문). 스펙상 quantity·price 는 문자열이다.
 * confirmHighValueOrder 는 코드가 자동으로 true 로 설정하지 않는다 (PRINCIPLES / CLAUDE.md 7번).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderCreateRequest(
        String clientOrderId, String symbol, String side, String orderType, String quantity, String price) {}
