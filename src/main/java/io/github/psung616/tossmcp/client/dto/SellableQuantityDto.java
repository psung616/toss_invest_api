package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** GET /api/v1/sellable-quantity 의 result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SellableQuantityDto(BigDecimal sellableQuantity) {}
