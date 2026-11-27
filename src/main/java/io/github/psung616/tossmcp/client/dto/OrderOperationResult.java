package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 취소·정정 응답 ({@code OrderOperationResponse}). orderId 는 정정/취소로 <b>새로 발급된</b> 주문 식별자(원주문과 다름). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderOperationResult(String orderId) {}
