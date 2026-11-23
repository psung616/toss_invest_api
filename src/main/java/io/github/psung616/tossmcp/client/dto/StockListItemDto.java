package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /api/v1/stocks/all?market= 의 result 원소 (openapi v1.2.28). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockListItemDto(String symbol, String name, String securityType, Boolean isCommonShare) {}
