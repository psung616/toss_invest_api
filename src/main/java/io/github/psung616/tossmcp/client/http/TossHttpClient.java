package io.github.psung616.tossmcp.client.http;

import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.Envelope;
import io.github.psung616.tossmcp.client.error.TossApiException;
import java.net.URI;
import java.util.function.Function;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * 요청 파이프라인: 토큰 첨부 → (필요 시) X-Tossinvest-Account → 전송 → 봉투 해제.
 * 401 expired-token / token-revoked 이면 토큰을 재발급해 한 번만 재시도한다.
 *
 * TODO(P2-2, P2-3): 레이트리미터, 429 Retry-After 재시도, X-RateLimit-Limit 반영
 */
@Component
public class TossHttpClient {

    private static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    private final RestClient restClient;
    private final TokenManager tokenManager;
    private final ErrorDecoder errorDecoder;

    TossHttpClient(RestClient restClient, TokenManager tokenManager, ErrorDecoder errorDecoder) {
        this.restClient = restClient;
        this.tokenManager = tokenManager;
        this.errorDecoder = errorDecoder;
    }

    /**
     * @param accountSeq 계좌 범위 API 면 accountSeq, 아니면 null
     * @param body       POST 바디. 없으면 null
     */
    public <T> T execute(
            HttpMethod method,
            Function<UriBuilder, URI> uri,
            Long accountSeq,
            Object body,
            ParameterizedTypeReference<Envelope<T>> responseType) {
        String token = tokenManager.getAccessToken();
        try {
            return send(token, method, uri, accountSeq, body, responseType);
        } catch (TossApiException e) {
            if (!e.isTokenExpired()) {
                throw e;
            }
            tokenManager.invalidate(token);
            return send(tokenManager.getAccessToken(), method, uri, accountSeq, body, responseType);
        }
    }

    private <T> T send(
            String token,
            HttpMethod method,
            Function<UriBuilder, URI> uri,
            Long accountSeq,
            Object body,
            ParameterizedTypeReference<Envelope<T>> responseType) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(uri).headers(headers -> {
            headers.setBearerAuth(token);
            if (accountSeq != null) {
                headers.set(ACCOUNT_HEADER, accountSeq.toString());
            }
        });
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        Envelope<T> envelope = spec.retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw errorDecoder.decode(response);
                })
                .body(responseType);
        if (envelope == null) {
            throw new TossApiException(0, null, "빈 응답", null);
        }
        return envelope.result();
    }
}
