package io.github.psung616.tossmcp.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.ExchangeRateDto;
import io.github.psung616.tossmcp.client.dto.HoldingsDto;
import io.github.psung616.tossmcp.client.dto.OrderDetailDto;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.ratelimit.FakeTime;
import io.github.psung616.tossmcp.client.ratelimit.RateLimiter;
import io.github.psung616.tossmcp.secret.Credentials;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** P3-4 엔드포인트: 경로·쿼리·계좌 헤더·응답 파싱 (openapi v1.2.28 형태의 지어낸 값). 실제 API 를 부르지 않는다. */
class TossApiAccountTest {

    private static final String BASE = "https://openapi.tossinvest.com";
    private static final String ACCOUNTS =
            "{\"result\":[{\"accountNo\":\"000-00-000000\",\"accountSeq\":7,\"accountType\":\"BROKERAGE\"}]}";

    @TempDir
    Path dir;

    private MockRestServiceServer server;
    private TossApi api;

    @BeforeEach
    void setUp() {
        FakeTime time = FakeTime.atKst("2026-11-27T10:00:00");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        TokenManager tokens = new TokenManager(
                c -> new TokenResponse("fake-token", "Bearer", 86400),
                () -> Optional.of(new Credentials("fake-client", "fake-secret")),
                new TokenCacheFile(dir.resolve("token.json")),
                time);
        api = new TossApi(new TossHttpClient(
                builder.build(),
                tokens,
                new ErrorDecoder(JsonMapper.builder().build()),
                new RateLimiter(time, time),
                new RetryPolicy(() -> 0L),
                time));
    }

    private void expectAccounts() {
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withSuccess(ACCOUNTS, MediaType.APPLICATION_JSON));
    }

    @Test
    void holdingsParsesSpecShapeWithAccountHeader() {
        expectAccounts();
        server.expect(requestTo(BASE + "/api/v1/holdings"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withSuccess("""
                        {"result":{
                          "totalPurchaseAmount":{"krw":"700000","usd":"100.00"},
                          "marketValue":{"amount":{"krw":"710000","usd":"120.50"},"amountAfterCost":{"krw":"709000","usd":"120.00"}},
                          "profitLoss":{"amount":{"krw":"10000","usd":"20.50"},"amountAfterCost":{"krw":"9000","usd":"20.00"},
                                        "rate":"0.0250","rateAfterCost":"0.0230"},
                          "dailyProfitLoss":{"amount":{"krw":"-1000","usd":null},"rate":"-0.0012"},
                          "items":[{"symbol":"005930","name":"가짜전자","marketCountry":"KR","currency":"KRW",
                                    "quantity":"10","lastPrice":"71000","averagePurchasePrice":"70000",
                                    "marketValue":{"purchaseAmount":"700000","amount":"710000","amountAfterCost":"709000"},
                                    "profitLoss":{"amount":"10000","amountAfterCost":"9000","rate":"0.0142","rateAfterCost":"0.0128"},
                                    "dailyProfitLoss":{"amount":"-1000","rate":"-0.0014"},
                                    "cost":{"commission":"100","tax":null},
                                    "someNewField":"ignored"}]
                        }}
                        """, MediaType.APPLICATION_JSON));

        HoldingsDto h = api.getHoldings();

        assertThat(h.marketValue().amount().usd()).isEqualByComparingTo("120.50");
        assertThat(h.dailyProfitLoss().amount().usd()).isNull();
        assertThat(h.items()).singleElement().satisfies(i -> {
            assertThat(i.quantity()).isEqualByComparingTo("10");
            assertThat(i.profitLoss().rate()).isEqualByComparingTo("0.0142");
            assertThat(i.cost().tax()).isNull();
        });
        server.verify();
    }

    @Test
    void exchangeRateNeedsNoAccountHeader() {
        server.expect(requestTo(BASE + "/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW"))
                .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                .andRespond(withSuccess(
                        "{\"result\":{\"baseCurrency\":\"USD\",\"quoteCurrency\":\"KRW\",\"rate\":\"1400.50\","
                                + "\"midRate\":\"1400.00\",\"basisPoint\":\"0\",\"rateChangeType\":\"EQUAL\","
                                + "\"validFrom\":\"2026-11-27T10:00:00+09:00\",\"validUntil\":\"2026-11-27T10:01:00+09:00\"}}",
                        MediaType.APPLICATION_JSON));

        ExchangeRateDto rate = api.getExchangeRate("USD", "KRW");

        assertThat(rate.rate()).isEqualByComparingTo("1400.50");
        server.verify();
    }

    @Test
    void sellableQuantity() {
        expectAccounts();
        server.expect(requestTo(BASE + "/api/v1/sellable-quantity?symbol=005930"))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withSuccess("{\"result\":{\"sellableQuantity\":\"3\"}}", MediaType.APPLICATION_JSON));

        assertThat(api.getSellableQuantity("005930").sellableQuantity()).isEqualByComparingTo("3");
        server.verify();
    }

    @Test
    void orderDetailEncodesOrderIdAsPathSegment() {
        expectAccounts();
        server.expect(requestTo(BASE + "/api/v1/orders/ab%2Fc%3Fd"))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withSuccess("""
                        {"result":{"orderId":"ab/c?d","symbol":"NVDA","side":"BUY","orderType":"LIMIT","timeInForce":"DAY",
                          "status":"PARTIAL_FILLED","price":"100.00","quantity":"5","orderAmount":null,"currency":"USD",
                          "orderedAt":"2026-11-27T23:30:00+09:00","canceledAt":null,
                          "execution":{"filledQuantity":"2","averageFilledPrice":"99.50","filledAmount":"199.00",
                                       "commission":"0.10","tax":null,"filledAt":"2026-11-27T23:31:00+09:00","settlementDate":null}}}
                        """, MediaType.APPLICATION_JSON));

        OrderDetailDto order = api.getOrder("ab/c?d");

        assertThat(order.status()).isEqualTo("PARTIAL_FILLED");
        assertThat(order.execution().filledQuantity()).isEqualByComparingTo("2");
        server.verify();
    }
}
