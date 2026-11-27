package io.github.psung616.tossmcp.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.config.TossProperties;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.secret.KeyringCredentialStore;
import io.github.psung616.tossmcp.secret.OsKeyring;
import io.github.psung616.tossmcp.service.AppStatusService;
import io.github.psung616.tossmcp.service.KeySetupService;
import io.github.psung616.tossmcp.web.api.KeysController;
import io.github.psung616.tossmcp.web.api.StatusController;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** WEB_SECURITY §4.2~4.5 요청 검사·로그인·세션·응답 헤더 (MockMvc, 서버 없이). 모든 값은 지어낸 가짜 값이다. */
class LocalAccessFilterTest {

    private static final int PORT = 8765;
    private static final String SELF = "127.0.0.1:" + PORT;

    private final MutableClock clock = new MutableClock();
    private final LaunchCodeStore codes = new LaunchCodeStore(clock);
    private final SessionStore sessions = new SessionStore(clock);
    private final MemoryKeyring keyring = new MemoryKeyring();
    private MockMvc mvc;

    @TempDir
    Path dir;

    /** 메모리 저장소 — 실제 OS 자격 증명 저장소는 건드리지 않는다. */
    static final class MemoryKeyring implements OsKeyring {
        Credentials stored;

        @Override
        public Optional<Credentials> read() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(String clientId, char[] clientSecret) {
            stored = new Credentials(clientId, new String(clientSecret));
        }

        @Override
        public void delete() {
            stored = null;
        }
    }

    @BeforeEach
    void setUp() {
        TossProperties props = new TossProperties(
                false,
                true,
                new BigDecimal("100000"),
                new BigDecimal("300000"),
                10,
                List.of(),
                false,
                false,
                false,
                5,
                Duration.ofMinutes(5));
        AppStatusService status = new AppStatusService(
                () -> Optional.of(new Credentials("fake-id", "fake-secret")), props, "0.1.0", () -> false);
        KeyringCredentialStore keyStore = new KeyringCredentialStore(keyring);
        TokenManager tokens = new TokenManager(
                c -> new TokenResponse("fake-token", "Bearer", 86400),
                keyStore,
                new TokenCacheFile(dir.resolve("token.json")),
                clock);
        KeySetupService keys = new KeySetupService(keyStore, tokens, clock);
        mvc = MockMvcBuilders.standaloneSetup(
                        new LoginController(codes, sessions), new StatusController(status), new KeysController(keys))
                .addFilters(new LocalAccessFilter(sessions))
                .build();
    }

    /** 브라우저가 같은 출처에서 보내는 정상 요청 모양. */
    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return withHost(builder, SELF);
    }

    /** Host 헤더는 여러 번 붙이면 첫 값이 쓰이므로, 다른 Host 를 시험할 때는 이 메서드로 처음부터 만든다. */
    private static MockHttpServletRequestBuilder withHost(MockHttpServletRequestBuilder builder, String host) {
        return builder.header("Host", host).with(r -> {
            r.setLocalPort(PORT);
            r.setRemoteAddr("127.0.0.1");
            return r;
        });
    }

    private Cookie login() throws Exception {
        String code = codes.issue();
        MockHttpServletResponse response = mvc.perform(local(get("/login").param("code", code)))
                .andExpect(status().isSeeOther())
                .andReturn()
                .getResponse();
        return response.getCookie(SessionStore.COOKIE);
    }

    // ---- 1. 원격 주소

    @Test
    void nonLoopbackRemoteAddressIsForbidden() throws Exception {
        Cookie cookie = login();
        mvc.perform(local(get("/api/status")).cookie(cookie).with(r -> {
                    r.setRemoteAddr("192.168.0.20");
                    return r;
                }))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    void ipv6LoopbackRemoteAddressIsAllowed() throws Exception {
        Cookie cookie = login();
        mvc.perform(local(get("/api/status")).cookie(cookie).with(r -> {
                    r.setRemoteAddr("0:0:0:0:0:0:0:1");
                    return r;
                }))
                .andExpect(status().isOk());
    }

    // ---- 2. Host (DNS rebinding)

    @Test
    void wrongHostIsMisdirected() throws Exception {
        Cookie cookie = login();
        for (String host : List.of("evil.example:" + PORT, "localhost:" + PORT, "127.0.0.1", "127.0.0.1:9999", "")) {
            mvc.perform(withHost(get("/api/status"), host).cookie(cookie))
                    .andExpect(status().is(421))
                    .andExpect(content().string(""));
        }
    }

    // ---- 3·4. Origin / Sec-Fetch-Site (CSRF)

    @Test
    void crossOriginIsForbidden() throws Exception {
        Cookie cookie = login();
        for (String origin : List.of("http://evil.example", "null", "http://localhost:" + PORT, "https://" + SELF)) {
            mvc.perform(local(get("/api/status")).cookie(cookie).header("Origin", origin))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void sameOriginIsAllowed() throws Exception {
        Cookie cookie = login();
        mvc.perform(local(get("/api/status"))
                        .cookie(cookie)
                        .header("Origin", "http://" + SELF)
                        .header("Sec-Fetch-Site", "same-origin"))
                .andExpect(status().isOk());
    }

    @Test
    void crossSiteFetchMetadataIsForbidden() throws Exception {
        Cookie cookie = login();
        for (String site : List.of("cross-site", "same-site")) {
            mvc.perform(local(get("/api/status")).cookie(cookie).header("Sec-Fetch-Site", site))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void loginLinkOpenedFromConsoleHasSecFetchSiteNone() throws Exception {
        String code = codes.issue();
        mvc.perform(local(get("/login").param("code", code)).header("Sec-Fetch-Site", "none"))
                .andExpect(status().isSeeOther());
    }

    @Test
    void crossSiteLoginAttemptIsForbiddenBeforeCodeIsChecked() throws Exception {
        String code = codes.issue();
        mvc.perform(local(get("/login").param("code", code)).header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden());
        // 거부된 요청은 코드를 소모하지 않는다
        mvc.perform(local(get("/login").param("code", code))).andExpect(status().isSeeOther());
    }

    // ---- 5. 메서드

    @Test
    void onlyGetAndHeadAreAllowed() throws Exception {
        Cookie cookie = login();
        mvc.perform(local(head("/api/status")).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(local(post("/api/status")).cookie(cookie)).andExpect(status().isMethodNotAllowed());
        mvc.perform(local(delete("/api/status")).cookie(cookie)).andExpect(status().isMethodNotAllowed());
        mvc.perform(local(request(HttpMethod.valueOf("TRACE"), "/api/status")).cookie(cookie))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void corsPreflightGetsNoCorsHeaders() throws Exception {
        mvc.perform(local(options("/api/status"))
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    // ---- 6. 세션

    @Test
    void missingOrInvalidSessionIsUnauthorized() throws Exception {
        mvc.perform(local(get("/api/status")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mvc.perform(local(get("/api/status")).cookie(new Cookie(SessionStore.COOKIE, "forged")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserNavigationWithoutSessionGetsFixedGuidePage() throws Exception {
        String body = mvc.perform(local(get("/")).header("Sec-Fetch-Mode", "navigate"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("Enter").doesNotContain("<script");
    }

    @Test
    void newLoginRevokesPreviousSession() throws Exception {
        Cookie first = login();
        Cookie second = login();
        mvc.perform(local(get("/api/status")).cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(local(get("/api/status")).cookie(second)).andExpect(status().isOk());
    }

    // ---- 로그인

    @Test
    void loginSetsStrictHttpOnlySessionCookieAndRedirectsHome() throws Exception {
        String code = codes.issue();
        MockHttpServletResponse response = mvc.perform(local(get("/login").param("code", code)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/"))
                .andReturn()
                .getResponse();
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith(SessionStore.COOKIE + "=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/")
                .doesNotContainIgnoringCase("Max-Age")
                .doesNotContainIgnoringCase("Expires")
                .doesNotContainIgnoringCase("Domain");
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
    }

    @Test
    void reusedExpiredOrWrongCodeIsRejected() throws Exception {
        String code = codes.issue();
        mvc.perform(local(get("/login").param("code", code))).andExpect(status().isSeeOther());
        mvc.perform(local(get("/login").param("code", code)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));

        String expiring = codes.issue();
        clock.advance(Duration.ofMinutes(3));
        mvc.perform(local(get("/login").param("code", expiring))).andExpect(status().isUnauthorized());

        mvc.perform(local(get("/login"))).andExpect(status().isUnauthorized());
        mvc.perform(local(get("/login").param("code", "wrong"))).andExpect(status().isUnauthorized());
    }

    // ---- 응답 헤더

    @Test
    void securityHeadersOnEveryResponse() throws Exception {
        Cookie cookie = login();
        List<MockHttpServletRequestBuilder> requests = List.of(
                local(get("/api/status")).cookie(cookie), // 200
                local(get("/api/status")), // 401
                local(get("/api/status")).cookie(cookie).header("Origin", "http://evil.example"), // 403
                withHost(get("/api/status"), "evil.example").cookie(cookie), // 421
                local(get("/nope")).cookie(cookie)); // 404
        for (MockHttpServletRequestBuilder req : requests) {
            MockHttpServletResponse r = mvc.perform(req).andReturn().getResponse();
            assertThat(r.getHeader("Content-Security-Policy"))
                    .contains("default-src 'none'")
                    .contains("script-src 'self'")
                    .contains("frame-ancestors 'none'");
            assertThat(r.getHeader("X-Frame-Options")).isEqualTo("DENY");
            assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(r.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(r.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(r.getHeader("Cross-Origin-Opener-Policy")).isEqualTo("same-origin");
            assertThat(r.getHeader("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
            assertThat(r.getHeader("Permissions-Policy")).contains("camera=()");
            assertThat(r.getHeaderNames()).noneMatch(h -> h.startsWith("Access-Control-"));
        }
    }

    // ---- /api/status (WS-DATA-2)

    @Test
    void statusShowsOnlyFlags() throws Exception {
        Cookie cookie = login();
        String body = mvc.perform(local(get("/api/status")).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.keysConfigured").value(true))
                .andExpect(jsonPath("$.keysFromEnvironment").value(false))
                .andExpect(jsonPath("$.ordersEnabled").value(false))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("fake-id").doesNotContain("fake-secret");
    }

    // ---- POST /api/keys (RC-003, WS-KEY · WS-REQ-7·8)

    private static final String KEY_JSON = "{\"clientId\":\"fake-id-9\",\"clientSecret\":\"fake-secret-9\"}";

    /**
     * 화면이 보내는 정상 POST 모양. 헤더는 여러 번 붙이면 첫 값이 쓰이므로, 바꿀 헤더는 {@code overrides}(이름, 값, …)로 넘긴다. 값이 null 이면 그 헤더를
     * 빼고 보낸다.
     */
    private static MockHttpServletRequestBuilder postKeys(Cookie cookie, String json, String... overrides) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", "http://" + SELF);
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put(LocalAccessFilter.LOCAL_HEADER, "1");
        for (int i = 0; i < overrides.length; i += 2) {
            headers.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder builder = local(post("/api/keys"))
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        headers.forEach((name, value) -> {
            if (value != null) {
                builder.header(name, value);
            }
        });
        return builder;
    }

    @Test
    void postKeysSavesAndNeverEchoesValues() throws Exception {
        Cookie cookie = login();
        String body = mvc.perform(postKeys(cookie, KEY_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.connected").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("fake-id-9").doesNotContain("fake-secret-9");
        assertThat(keyring.stored).isEqualTo(new Credentials("fake-id-9", "fake-secret-9"));
    }

    @Test
    void postKeysRequiresSession() throws Exception {
        mvc.perform(postKeys(new Cookie(SessionStore.COOKIE, "forged"), KEY_JSON))
                .andExpect(status().isUnauthorized());
        assertThat(keyring.stored).isNull();
    }

    @Test
    void postKeysRequiresCustomHeaderAndJson() throws Exception {
        Cookie cookie = login();
        mvc.perform(postKeys(cookie, KEY_JSON, LocalAccessFilter.LOCAL_HEADER, "0"))
                .andExpect(status().isForbidden());
        mvc.perform(postKeys(cookie, KEY_JSON, LocalAccessFilter.LOCAL_HEADER, null))
                .andExpect(status().isForbidden());
        mvc.perform(postKeys(cookie, "clientId=a&clientSecret=b").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(postKeys(cookie, KEY_JSON).contentType(MediaType.TEXT_PLAIN))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(keyring.stored).isNull();
    }

    @Test
    void postKeysRejectsCrossSiteRequests() throws Exception {
        Cookie cookie = login();
        mvc.perform(postKeys(cookie, KEY_JSON, "Origin", "http://evil.example")).andExpect(status().isForbidden());
        mvc.perform(postKeys(cookie, KEY_JSON, "Sec-Fetch-Site", "cross-site")).andExpect(status().isForbidden());
        assertThat(keyring.stored).isNull();
    }

    @Test
    void postKeysBodyLimitedTo4Kb() throws Exception {
        Cookie cookie = login();
        String big = "{\"clientId\":\"" + "a".repeat(LocalAccessFilter.MAX_POST_BODY) + "\",\"clientSecret\":\"b\"}";
        mvc.perform(postKeys(cookie, big)).andExpect(status().isPayloadTooLarge());
        assertThat(keyring.stored).isNull();
    }

    @Test
    void postOnlyAllowedOnKeysPath() throws Exception {
        Cookie cookie = login();
        mvc.perform(local(post("/api/status"))
                        .cookie(cookie)
                        .header(LocalAccessFilter.LOCAL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET, HEAD"));
        mvc.perform(local(delete("/api/keys")).cookie(cookie))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"));
    }

    @Test
    void invalidOrMalformedKeyInputIs400WithoutEcho() throws Exception {
        Cookie cookie = login();
        mvc.perform(postKeys(cookie, "{\"clientId\":\"\",\"clientSecret\":\"fake-secret-9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_input"));
        String body = mvc.perform(postKeys(cookie, "{\"clientId\":\"a\",\"clientSecret\": fake-secret-unquoted}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("fake-secret");
        assertThat(keyring.stored).isNull();
    }
}
