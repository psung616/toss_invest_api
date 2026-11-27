package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /api/v1/orders/{orderId}/modify 바디 ({@code OrderModifyRequest}, openapi v1.2.28). null 필드는 보내지 않는다:
 * KR 은 quantity 필수(양의 정수), US 는 quantity 전달 불가, price 는 LIMIT 일 때만.
 *
 * <p>{@code confirmHighValueOrder} 필드는 <b>두지 않는다</b> — 코드가 자동으로 true 를 보내지 않게(절대 원칙 7). 1억 이상 정정은 가드레일이 막는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderModifyRequest(String orderType, String quantity, String price) {}
