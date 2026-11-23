package io.github.psung616.tossmcp.client;

import io.github.psung616.tossmcp.client.dto.AccountDto;
import io.github.psung616.tossmcp.client.dto.BuyingPowerDto;
import io.github.psung616.tossmcp.client.dto.Envelope;
import io.github.psung616.tossmcp.client.dto.MarketCalendarDto;
import io.github.psung616.tossmcp.client.dto.OrderCreateRequest;
import io.github.psung616.tossmcp.client.dto.OrderCreateResult;
import io.github.psung616.tossmcp.client.dto.OrderListDto;
import io.github.psung616.tossmcp.client.dto.PriceDto;
import io.github.psung616.tossmcp.client.dto.StockListItemDto;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.client.http.TossHttpClient;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * 엔드포인트별 메서드. 도구(tool)·주문(order) 계층이 토스증권 API 에 접근하는 유일한 진입점.
 * 경로·파라미터는 docs/api/openapi.json (v1.2.28) 기준.
 *
 * TODO(P2-5, P2-6): 호가·상하한가·캔들·종목 마스터·보유 종목·매도 가능 수량·주문 상세 등
 */
@Component
public class TossApi {

    private final TossHttpClient http;
    private volatile Long accountSeq;

    public TossApi(TossHttpClient http) {
        this.http = http;
    }

    public List<PriceDto> getPrices(String symbols) {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/prices").queryParam("symbols", symbols).build(),
                null,
                null,
                new ParameterizedTypeReference<Envelope<List<PriceDto>>>() {});
    }

    /** 마켓별 거래 가능 종목 전체 (STOCK_ALL 그룹, 초당 1회 — 하루 1회 조회 후 로컬 캐시 권장). */
    public List<StockListItemDto> listStocks(String market) {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/stocks/all")
                        .queryParam("market", market)
                        .build(),
                null,
                null,
                new ParameterizedTypeReference<Envelope<List<StockListItemDto>>>() {});
    }

    /** 국내 장 운영 정보 (MARKET_INFO 그룹). */
    public MarketCalendarDto getMarketCalendarKr() {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/market-calendar/KR").build(),
                null,
                null,
                new ParameterizedTypeReference<Envelope<MarketCalendarDto>>() {});
    }

    public List<AccountDto> getAccounts() {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/accounts").build(),
                null,
                null,
                new ParameterizedTypeReference<Envelope<List<AccountDto>>>() {});
    }

    public BuyingPowerDto getBuyingPower(String currency) {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/buying-power")
                        .queryParam("currency", currency)
                        .build(),
                accountSeq(),
                null,
                new ParameterizedTypeReference<Envelope<BuyingPowerDto>>() {});
    }

    public OrderListDto listOrders(String status) {
        return http.execute(
                HttpMethod.GET,
                uri -> uri.path("/api/v1/orders").queryParam("status", status).build(),
                accountSeq(),
                null,
                new ParameterizedTypeReference<Envelope<OrderListDto>>() {});
    }

    public OrderCreateResult createOrder(OrderCreateRequest request) {
        return http.execute(
                HttpMethod.POST,
                uri -> uri.path("/api/v1/orders").build(),
                accountSeq(),
                request,
                new ParameterizedTypeReference<Envelope<OrderCreateResult>>() {});
    }

    /**
     * 위탁계좌(BROKERAGE)의 accountSeq. /accounts 는 레이트리밋이 초당 1회라 한 번만 조회해 캐시한다.
     */
    private Long accountSeq() {
        Long cached = accountSeq;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (accountSeq == null) {
                accountSeq = getAccounts().stream()
                        .filter(a -> "BROKERAGE".equals(a.accountType()))
                        .findFirst()
                        .map(AccountDto::accountSeq)
                        .orElseThrow(() -> new TossApiException(0, "account-not-found", "위탁계좌를 찾을 수 없습니다.", null));
            }
            return accountSeq;
        }
    }
}
