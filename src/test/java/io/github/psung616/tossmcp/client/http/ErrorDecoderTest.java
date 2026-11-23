package io.github.psung616.tossmcp.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.error.TossApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import tools.jackson.databind.json.JsonMapper;

/** 픽스처는 모두 손으로 지어낸 가짜 응답이다. */
class ErrorDecoderTest {

    private final ErrorDecoder decoder = new ErrorDecoder(JsonMapper.builder().build());

    private TossApiException decode(HttpStatus status, String body) throws IOException {
        return decoder.decode(new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status));
    }

    @Test
    void apiEnvelope() throws IOException {
        TossApiException e = decode(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "{\"error\":{\"requestId\":\"req-1\",\"code\":\"insufficient-buying-power\",\"message\":\"m\"}}");

        assertThat(e.status()).isEqualTo(422);
        assertThat(e.code()).isEqualTo("insufficient-buying-power");
        assertThat(e.requestId()).isEqualTo("req-1");
    }

    @Test
    void edgeBlockedUsesReferenceId() throws IOException {
        TossApiException e = decode(
                HttpStatus.UNAUTHORIZED,
                "{\"error\":{\"referenceId\":\"ref-1\",\"code\":\"edge-blocked\",\"message\":\"m\"}}");

        assertThat(e.code()).isEqualTo("edge-blocked");
        assertThat(e.requestId()).isEqualTo("ref-1");
    }

    @Test
    void oauthStyleError() throws IOException {
        TossApiException e =
                decode(HttpStatus.BAD_REQUEST, "{\"error\":\"access_denied\",\"error_description\":\"d\"}");

        assertThat(e.code()).isEqualTo("access_denied");
        assertThat(e.toUserMessage()).contains("허용 IP");
    }

    @Test
    void nonJsonBody() throws IOException {
        TossApiException e = decode(HttpStatus.BAD_GATEWAY, "<html>bad gateway</html>");

        assertThat(e.status()).isEqualTo(502);
        assertThat(e.code()).isNull();
    }
}
