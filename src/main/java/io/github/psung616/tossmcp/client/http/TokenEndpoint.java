package io.github.psung616.tossmcp.client.http;

import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.secret.Credentials;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /oauth2/token (Client Credentials, form-encoded). TokenManager 만 사용한다.
 */
@Component
public class TokenEndpoint {

    private final RestClient restClient;
    private final ErrorDecoder errorDecoder;

    TokenEndpoint(RestClient restClient, ErrorDecoder errorDecoder) {
        this.restClient = restClient;
        this.errorDecoder = errorDecoder;
    }

    /**
     * Spring 컨텍스트 없이 쓰는 인스턴스 (setup 의 연결 테스트용). 허용 호스트 검사·타임아웃은 동일하다.
     */
    public static TokenEndpoint standalone() {
        RestClient restClient = TossRestClientConfig.create(RestClient.builder());
        return new TokenEndpoint(
                restClient, new ErrorDecoder(JsonMapper.builder().build()));
    }

    public TokenResponse issue(Credentials credentials) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", credentials.clientId());
        form.add("client_secret", credentials.clientSecret());

        return restClient
                .post()
                .uri("/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw errorDecoder.decode(response);
                })
                .body(TokenResponse.class);
    }
}
