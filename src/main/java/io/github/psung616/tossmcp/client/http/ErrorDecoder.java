package io.github.psung616.tossmcp.client.http;

import io.github.psung616.tossmcp.client.dto.ErrorEnvelope;
import io.github.psung616.tossmcp.client.error.TossApiException;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실패 응답 봉투를 {@link TossApiException} 으로 변환한다.
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
        byte[] body = response.getBody().readAllBytes();
        try {
            ErrorEnvelope envelope = jsonMapper.readValue(body, ErrorEnvelope.class);
            if (envelope != null && envelope.error() != null) {
                ErrorEnvelope.ErrorBody error = envelope.error();
                String id = requestId != null ? requestId : error.requestId();
                return new TossApiException(status, error.code(), error.message(), id);
            }
            // OAuth2 토큰 엔드포인트는 {"error": "access_denied"} 형식일 수 있다.
        } catch (RuntimeException ignored) {
            // 봉투가 아닌 응답(HTML, 빈 바디 등)은 아래 기본 예외로 처리
        }
        return new TossApiException(status, null, "HTTP " + status, requestId);
    }
}
