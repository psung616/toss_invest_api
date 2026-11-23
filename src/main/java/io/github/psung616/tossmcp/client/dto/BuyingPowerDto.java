package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** GET /api/v1/buying-power 의 result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuyingPowerDto(String currency, BigDecimal cashBuyingPower) {}
