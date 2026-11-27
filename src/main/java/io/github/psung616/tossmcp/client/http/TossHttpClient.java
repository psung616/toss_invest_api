package io.github.psung616.tossmcp.client.http;

import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.Envelope;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.client.ratelimit.RateLimitGroup;
import io.github.psung616.tossmcp.client.ratelimit.RateLimiter;
import io.github.psung616.tossmcp.client.ratelimit.Sleeper;
import java.net.URI;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * 요청 파이프라인: 레이트리미터 → 토큰 첨부 → (필요 시) X-Tossinvest-Account → 전송 → 봉투 해제.
 *
 * <ul>
 *   <li>모든 전송(재시도 포함)은 그룹별 {@link RateLimiter} 를 거친다. 응답의 {@code X-RateLimit-Limit} 으로 한도를 낮춘다.
 *   <li>401 expired-token / token-revoked → 토큰 재발급 후 1회 재시도.
 *   <li>429 → {@link RetryPolicy}(Retry-After, 백오프·지터) 로 최대 3회 재시도. 주문 생성은 clientOrderId 멱등키(10분) 덕분에
 *       재시도해도 중복 주문이 되지 않는다.
 *   <li>그 밖의 실패는 재시도하지 않고 {@link TossApiException}(requestId 보존)으로 던진다.
 * </ul>
 */
@Component
public class TossHttpClient {

    private static final Logger log = LoggerFactory.getLogger(TossHttpClient.class);
    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";

    private final RestClient restClient;
    private final TokenManager tokenManager;
    private final ErrorDecoder errorDecoder;
    private final RateLimiter rateLimiter;
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;

    TossHttpClient(
            RestClient restClient,
            TokenManager tokenManager,
            ErrorDecoder errorDecoder,
            RateLimiter rateLimiter,
            RetryPolicy retryPolicy,
            Sleeper sleeper) {
        this.restClient = restClient;
        this.tokenManager = tokenManager;
        this.errorDecoder = errorDecoder;
        this.rateLimiter = rateLimiter;
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    /**
     * @param group      이 엔드포인트의 레이트리밋 그룹 (docs/design/TOSS_API_NOTES.md "스펙 확인 결과" 표)
     * @param accountSeq 계좌 범위 API 면 accountSeq, 아니면 null
     * @param body       POST 바디. 없으면 null
     */
    public <T> T execute(
            HttpMethod method,
            RateLimitGroup group,
            Function<UriBuilder, URI> uri,
            Long accountSeq,
            Object body,
            ParameterizedTypeReference<Envelope<T>> responseType) {
        for (int attempt = 1; ; attempt++) {
            try {
                return executeWithTokenRetry(method, group, uri, accountSeq, body, responseType);
            } catch (TossApiException e) {
                if (!e.isRateLimited()) {
                    throw e;
                }
                Duration delay =
                        retryPolicy.delayFor(attempt, e.retryAfterSeconds()).orElseThrow(() -> e);
                log.warn("Rate limited (group={}, attempt={}), retrying in {} ms", group, attempt, delay.toMillis());
                sleeper.sleep(delay);
            }
        }
    }

    private <T> T executeWithTokenRetry(
            HttpMethod method,
            RateLimitGroup group,
            Function<UriBuilder, URI> uri,
            Long accountSeq,
            Object body,
            ParameterizedTypeReference<Envelope<T>> responseType) {
        String token = tokenManager.getAccessToken();
        try {
            return send(token, method, group, uri, accountSeq, body, responseType);
        } catch (TossApiException e) {
            if (!e.isTokenExpired()) {
                throw e;
            }
            tokenManager.invalidate(token);
            return send(tokenManager.getAccessToken(), method, group, uri, accountSeq, body, responseType);
        }
    }

    private <T> T send(
            String token,
            HttpMethod method,
            RateLimitGroup group,
            Function<UriBuilder, URI> uri,
            Long accountSeq,
            Object body,
            ParameterizedTypeReference<Envelope<T>> responseType) {
        rateLimiter.acquire(group);
        RestClient.RequestBodySpec spec = restClient.method(method).uri(uri).headers(headers -> {
            headers.setBearerAuth(token);
            if (accountSeq != null) {
                headers.set(ACCOUNT_HEADER, accountSeq.toString());
            }
        });
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        ResponseEntity<Envelope<T>> entity = spec.retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    rateLimiter.observeLimit(group, response.getHeaders().getFirst(LIMIT_HEADER));
                    throw errorDecoder.decode(response);
                })
                .toEntity(responseType);
        rateLimiter.observeLimit(group, entity.getHeaders().getFirst(LIMIT_HEADER));
        Envelope<T> envelope = entity.getBody();
        if (envelope == null) {
            throw new TossApiException(0, null, "빈 응답", null);
        }
        return envelope.result();
    }
}
