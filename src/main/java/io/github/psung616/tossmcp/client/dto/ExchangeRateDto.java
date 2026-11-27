package io.github.psung616.tossmcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * GET /api/v1/exchange-rate 의 result ({@code ExchangeRateResponse}). 1분 주기 표준 환율(실제 주문 거래 환율과 다를 수 있음).
 * {@code rate} 는 base 1 단위당 quote 금액으로 해석한다(baseCurrency=USD, quoteCurrency=KRW → 1 USD 당 KRW). — 표준 표기 기준 해석, 실호출로 확인 필요.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRateDto(
        String baseCurrency,
        String quoteCurrency,
        BigDecimal rate,
        BigDecimal midRate,
        BigDecimal basisPoint,
        String rateChangeType,
        String validFrom,
        String validUntil) {}
