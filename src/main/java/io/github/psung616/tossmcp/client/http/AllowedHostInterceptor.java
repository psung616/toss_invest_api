package io.github.psung616.tossmcp.client.http;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * "외부로는 토스증권에만 통신한다"를 코드 한 곳에서 보장한다.
 * https + 호스트 정확 일치가 아니면 요청을 보내기 전에 거부한다.
 */
public class AllowedHostInterceptor implements ClientHttpRequestInterceptor {

    public static final Set<String> ALLOWED_HOSTS = Set.of("openapi.tossinvest.com", "openapi-ws.tossinvest.com");

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        check(request.getURI());
        return execution.execute(request, body);
    }

    static void check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) || !ALLOWED_HOSTS.contains(host) || uri.getUserInfo() != null) {
            throw new DisallowedHostException("허용되지 않은 호스트로의 요청을 차단했습니다: " + scheme + "://" + host);
        }
    }
}
