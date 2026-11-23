package io.github.psung616.tossmcp.client.http;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 이 애플리케이션의 유일한 RestClient. client.http 패키지 밖에서는 RestClient 를 직접 쓰지 않는다.
 */
@Configuration
public class TossRestClientConfig {

    public static final String BASE_URL = "https://openapi.tossinvest.com";

    @Bean
    RestClient tossRestClient(RestClient.Builder builder) {
        return create(builder);
    }

    static RestClient create(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return builder.baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .requestInterceptor(new AllowedHostInterceptor())
                .build();
    }
}
