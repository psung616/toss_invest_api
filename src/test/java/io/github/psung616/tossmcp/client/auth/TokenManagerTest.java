package io.github.psung616.tossmcp.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 토큰 값은 모두 가짜. */
class TokenManagerTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final CredentialStore credentials = () -> Optional.of(new Credentials("fake-client", "fake-secret"));

    /** 호출 횟수를 세는 가짜 발급자. 매번 다른 토큰을 준다. */
    static final class CountingIssuer implements TokenIssuer {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public TokenResponse issue(Credentials c) {
            int n = calls.incrementAndGet();
            return new TokenResponse("token-" + n, "Bearer", 86400);
        }
    }

    private TokenManager manager(TokenIssuer issuer) {
        return new TokenManager(issuer, credentials, new TokenCacheFile(dir.resolve("token.json")), clock);
    }

    @Test
    void reusesTokenAndWritesCacheWithoutClientIdInPlainText() throws Exception {
        CountingIssuer issuer = new CountingIssuer();
        TokenManager m = manager(issuer);

        assertThat(m.getAccessToken()).isEqualTo("token-1");
        assertThat(m.getAccessToken()).isEqualTo("token-1");
        assertThat(issuer.calls).hasValue(1);
        assertThat(Files.readString(dir.resolve("token.json"))).doesNotContain("fake-client");
    }

    @Test
    void secondProcessReusesCachedToken() {
        CountingIssuer issuer = new CountingIssuer();
        manager(issuer).getAccessToken();

        // 같은 캐시 파일을 쓰는 다른 인스턴스(= 다른 프로세스 역할)
        assertThat(manager(issuer).getAccessToken()).isEqualTo("token-1");
        assertThat(issuer.calls).hasValue(1);
    }

    @Test
    void concurrentRequestsIssueOnlyOnce() throws Exception {
        CountingIssuer issuer = new CountingIssuer();
        List<TokenManager> managers = List.of(manager(issuer), manager(issuer));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                TokenManager m = managers.get(i % 2);
                tasks.add(m::getAccessToken);
            }
            for (Future<String> f : pool.invokeAll(tasks)) {
                assertThat(f.get()).isEqualTo("token-1");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(issuer.calls).hasValue(1);
    }

    @Test
    void invalidateThenReissueOnce() {
        CountingIssuer issuer = new CountingIssuer();
        TokenManager m = manager(issuer);
        String first = m.getAccessToken();

        m.invalidate(first);

        assertThat(m.getAccessToken()).isEqualTo("token-2");
        assertThat(m.getAccessToken()).isEqualTo("token-2");
        assertThat(issuer.calls).hasValue(2);
    }

    @Test
    void doesNotReissueWhenAnotherProcessAlreadyRefreshed() {
        CountingIssuer issuer = new CountingIssuer();
        TokenManager a = manager(issuer);
        TokenManager b = manager(issuer);
        String old = a.getAccessToken(); // token-1, 둘 다 공유
        b.getAccessToken();

        // a 가 401 을 받아 재발급 → token-2 가 캐시에 기록됨
        a.invalidate(old);
        assertThat(a.getAccessToken()).isEqualTo("token-2");

        // b 도 낡은 token-1 로 401 → 캐시에 이미 token-2 가 있으므로 재발급하지 않는다
        b.invalidate(old);
        assertThat(b.getAccessToken()).isEqualTo("token-2");
        assertThat(issuer.calls).hasValue(2);
    }

    @Test
    void ignoresCacheOfDifferentClient() {
        CountingIssuer issuer = new CountingIssuer();
        manager(issuer).getAccessToken();

        CredentialStore other = () -> Optional.of(new Credentials("other-client", "s"));
        TokenManager m = new TokenManager(issuer, other, new TokenCacheFile(dir.resolve("token.json")), clock);

        assertThat(m.getAccessToken()).isEqualTo("token-2");
    }

    @Test
    void issueFailurePropagates() {
        TokenManager m = manager(c -> {
            throw new TossApiException(401, "invalid_client", "bad", null);
        });

        assertThatThrownBy(m::getAccessToken).isInstanceOf(TossApiException.class);
    }

    @Test
    void missingCredentialsGivesSetupHint() {
        TokenManager m = new TokenManager(
                new CountingIssuer(), Optional::empty, new TokenCacheFile(dir.resolve("token.json")), clock);

        assertThatThrownBy(m::getAccessToken).hasMessageContaining("setup");
    }

    @Test
    void reissueAfterKeyChangeDropsOldTokenFromMemoryAndCache() {
        CountingIssuer issuer = new CountingIssuer();
        TokenManager m = manager(issuer);
        assertThat(m.getAccessToken()).isEqualTo("token-1");

        m.reissueAfterKeyChange();

        assertThat(m.getAccessToken()).isEqualTo("token-2");
        assertThat(manager(issuer).getAccessToken()).as("다른 프로세스도 새 토큰을 받는다").isEqualTo("token-2");
        assertThat(issuer.calls).hasValue(2);
    }

    @Test
    void failedReissueLeavesNoOldToken() {
        CountingIssuer ok = new CountingIssuer();
        manager(ok).getAccessToken();
        TokenManager m = manager(c -> {
            throw new TossApiException(401, "invalid_client", "Client authentication failed", null);
        });

        assertThatThrownBy(m::reissueAfterKeyChange).isInstanceOf(TossApiException.class);
        assertThat(Files.exists(dir.resolve("token.json")))
                .as("이전 키의 토큰을 남기지 않는다")
                .isFalse();
    }
}
