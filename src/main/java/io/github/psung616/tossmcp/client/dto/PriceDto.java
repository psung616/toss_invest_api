package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** GET /api/v1/prices 의 result 원소. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PriceDto(String symbol, String timestamp, BigDecimal lastPrice, String currency) {}
