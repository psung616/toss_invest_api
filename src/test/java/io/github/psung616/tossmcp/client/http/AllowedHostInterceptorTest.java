package io.github.psung616.tossmcp.client.http;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedHostInterceptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://openapi.tossinvest.com/api/v1/prices", "https://openapi-ws.tossinvest.com/ws/v1"})
    void allowsTossHosts(String uri) {
        assertThatCode(() -> AllowedHostInterceptor.check(URI.create(uri))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com/",
                "https://openapi.tossinvest.com.evil.io/",
                "https://evil.io/openapi.tossinvest.com",
                "http://openapi.tossinvest.com/",
                "https://1.2.3.4/",
                "https://user@openapi.tossinvest.com/"
            })
    void rejectsOtherHosts(String uri) {
        assertThatThrownBy(() -> AllowedHostInterceptor.check(URI.create(uri)))
                .isInstanceOf(DisallowedHostException.class);
    }
}
