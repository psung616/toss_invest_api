package io.github.psung616.tossmcp.client.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.Envelope;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.client.ratelimit.FakeTime;
import io.github.psung616.tossmcp.client.ratelimit.RateLimitGroup;
import io.github.psung616.tossmcp.client.ratelimit.RateLimiter;
import io.github.psung616.tossmcp.secret.Credentials;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 모든 토큰·값은 지어낸 가짜다. 실제 API 를 호출하지 않는다. */
class TossHttpClientTest {

    private static final String BASE = "https://openapi.tossinvest.com";
    private static final ParameterizedTypeReference<Envelope<Map<String, String>>> MAP =
            new ParameterizedTypeReference<>() {};

    @TempDir
    Path dir;

    private final FakeTime time = FakeTime.atKst("2026-11-25T10:00:00");
    private final AtomicInteger issued = new AtomicInteger();
    private MockRestServiceServer server;
    private RateLimiter limiter;
    private TossHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        TokenManager tokens = new TokenManager(
                c -> new TokenResponse("fake-token-" + issued.incrementAndGet(), "Bearer", 86400),
                () -> Optional.of(new Credentials("fake-client", "fake-secret")),
                new TokenCacheFile(dir.resolve("token.json")),
                time);
        limiter = new RateLimiter(time, time);
        client = new TossHttpClient(
                builder.build(),
                tokens,
                new ErrorDecoder(JsonMapper.builder().build()),
                limiter,
                new RetryPolicy(() -> 0L),
                time);
    }

    private Map<String, String> get(RateLimitGroup group, Long accountSeq) {
        return client.execute(
                HttpMethod.GET, group, uri -> uri.path("/api/v1/x").build(), accountSeq, null, MAP);
    }

    private static String error(String code) {
        return "{\"error\":{\"requestId\":\"req-1\",\"code\":\"" + code + "\",\"message\":\"m\"}}";
    }

    @Test
    void unwrapsEnvelopeAndSendsAuthAndAccountHeaders() {
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-token-1"))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withSuccess("{\"result\":{\"a\":\"b\"}}", MediaType.APPLICATION_JSON));

        assertThat(get(RateLimitGroup.ASSET, 7L)).containsEntry("a", "b");
        server.verify();
    }

    @Test
    void noAccountHeaderForMarketData() {
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        get(RateLimitGroup.MARKET_DATA, null);
        server.verify();
    }

    @Test
    void successHeaderLowersLimit() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-RateLimit-Limit", "4");
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON)
                        .headers(h));

        get(RateLimitGroup.MARKET_DATA, null);
        assertThat(limiter.effectiveLimit(RateLimitGroup.MARKET_DATA)).isEqualTo(4);
    }

    @Test
    void retriesAfter429UsingRetryAfter() {
        HttpHeaders h = new HttpHeaders();
        h.set("Retry-After", "2");
        h.set("X-RateLimit-Limit", "3");
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h)
                        .body(error("rate-limit-exceeded")));
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andRespond(withSuccess("{\"result\":{\"ok\":\"1\"}}", MediaType.APPLICATION_JSON));

        assertThat(get(RateLimitGroup.MARKET_DATA, null)).containsEntry("ok", "1");
        assertThat(time.sleeps).contains(Duration.ofSeconds(2));
        assertThat(limiter.effectiveLimit(RateLimitGroup.MARKET_DATA)).isEqualTo(3);
        server.verify();
    }

    @Test
    void givesUpAfterThreeRetries() {
        server.expect(ExpectedCount.times(4), requestTo(BASE + "/api/v1/x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error("rate-limit-exceeded")));

        assertThatThrownBy(() -> get(RateLimitGroup.MARKET_DATA, null))
                .isInstanceOf(TossApiException.class)
                .satisfies(e -> assertThat(((TossApiException) e).status()).isEqualTo(429));
        assertThat(time.sleeps)
                .containsSubsequence(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)));
        server.verify();
    }

    @Test
    void reissuesTokenOnceOn401() {
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-token-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error("expired-token")));
        server.expect(requestTo(BASE + "/api/v1/x"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-token-2"))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        get(RateLimitGroup.MARKET_DATA, null);
        assertThat(issued).hasValue(2);
        server.verify();
    }

    @Test
    void otherErrorsAreNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/x"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error("insufficient-buying-power")));

        assertThatThrownBy(() -> get(RateLimitGroup.ORDER, 7L))
                .isInstanceOf(TossApiException.class)
                .satisfies(e -> {
                    TossApiException t = (TossApiException) e;
                    assertThat(t.code()).isEqualTo("insufficient-buying-power");
                    assertThat(t.requestId()).isEqualTo("req-1");
                });
        assertThat(time.sleeps).isEmpty();
        server.verify();
    }

    @Test
    void everyRequestPassesThroughLimiter() {
        server.expect(ExpectedCount.times(2), requestTo(BASE + "/api/v1/x"))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        get(RateLimitGroup.ACCOUNT, null);
        get(RateLimitGroup.ACCOUNT, null);
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(1));
    }
}
