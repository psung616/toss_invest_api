package io.github.psung616.tossmcp.client.http;

import io.github.psung616.tossmcp.client.error.TossApiException;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실패 응답을 {@link TossApiException} 으로 변환한다. 지원 형식:
 * <ul>
 *   <li>API 봉투: {@code {"error": {"requestId", "code", "message"}}}</li>
 *   <li>엣지 차단: {@code {"error": {"referenceId", "code", "message"}}} (2026-11-23 실측)</li>
 *   <li>OAuth2 토큰 엔드포인트: {@code {"error": "access_denied", "error_description": ...}}</li>
 * </ul>
 */
@Component
class ErrorDecoder {

    private final JsonMapper jsonMapper;

    ErrorDecoder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    TossApiException decode(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        Integer retryAfter = parseSeconds(response.getHeaders().getFirst("Retry-After"));
        byte[] body = response.getBody().readAllBytes();
        try {
            Map<?, ?> root = jsonMapper.readValue(body, Map.class);
            Object error = root.get("error");
            if (error instanceof Map<?, ?> e) {
                String id = requestId != null ? requestId : firstString(e.get("requestId"), e.get("referenceId"));
                return new TossApiException(
                        status, firstString(e.get("code")), firstString(e.get("message")), id, retryAfter);
            }
            if (error instanceof String oauthCode) {
                return new TossApiException(
                        status,
                        oauthCode,
                        firstString(root.get("error_description"), oauthCode),
                        requestId,
                        retryAfter);
            }
        } catch (RuntimeException ignored) {
            // 봉투가 아닌 응답(HTML, 빈 바디 등)은 아래 기본 예외로 처리
        }
        return new TossApiException(status, null, "HTTP " + status, requestId, retryAfter);
    }

    /** Retry-After 는 초 단위 정수만 해석한다(HTTP-date 형식은 무시 → 백오프 사용). */
    private static Integer parseSeconds(String value) {
        if (value == null) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(value.trim());
            return seconds >= 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstString(Object... values) {
        for (Object v : values) {
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
