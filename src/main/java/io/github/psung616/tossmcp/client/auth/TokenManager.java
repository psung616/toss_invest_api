package io.github.psung616.tossmcp.client.auth;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile.CachedToken;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 액세스 토큰 발급·캐시. 클라이언트당 유효 토큰은 1개이므로 같은 키를 쓰는 모든 프로세스가
 * {@link TokenCacheFile} 로 토큰 하나를 공유한다.
 *
 * <ul>
 *   <li>메모리 토큰이 유효하면 그대로 사용</li>
 *   <li>아니면 파일 잠금 안에서 캐시를 읽고, 유효하면 사용 / 없으면 발급 후 캐시에 기록</li>
 *   <li>401 로 실패한 토큰은 {@link #invalidate(String)} — 다른 프로세스가 이미 새 토큰을 받아 뒀다면 재발급하지 않고 그것을 쓴다</li>
 * </ul>
 */
public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final TokenIssuer issuer;
    private final CredentialStore credentialStore;
    private final TokenCacheFile cache;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public TokenManager(TokenIssuer issuer, CredentialStore credentialStore, TokenCacheFile cache, Clock clock) {
        this.issuer = issuer;
        this.credentialStore = credentialStore;
        this.cache = cache;
        this.clock = clock;
    }

    public String getAccessToken() {
        lock.lock();
        try {
            if (isUsable(accessToken, expiresAt)) {
                return accessToken;
            }
            Credentials credentials = credentials();
            CachedToken token = cache.withLock(() -> {
                Optional<CachedToken> cached = cache.read(credentials.clientId());
                if (cached.isPresent()
                        && isUsable(cached.get().accessToken(), cached.get().expiresAt())) {
                    return cached.get();
                }
                return issueAndStore(credentials);
            });
            accessToken = token.accessToken();
            expiresAt = token.expiresAt();
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 401(expired-token / token-revoked)을 받은 토큰을 무효화한다. 캐시 파일의 토큰이 같은 것일 때만 지운다
     * (다른 프로세스가 이미 새로 받아 둔 토큰은 지우지 않는다).
     */
    public void invalidate(String failedToken) {
        lock.lock();
        try {
            if (failedToken == null || failedToken.equals(accessToken)) {
                accessToken = null;
                expiresAt = Instant.EPOCH;
            }
            Optional<Credentials> credentials = credentialStore.load();
            if (credentials.isEmpty()) {
                return;
            }
            cache.withLock(() -> {
                cache.read(credentials.get().clientId())
                        .filter(c -> failedToken == null || c.accessToken().equals(failedToken))
                        .ifPresent(c -> cache.delete());
                return null;
            });
        } finally {
            lock.unlock();
        }
    }

    private CachedToken issueAndStore(Credentials credentials) {
        TokenResponse response = issuer.issue(credentials);
        if (response == null || response.accessToken() == null) {
            throw new TossApiException(0, null, "토큰 응답이 비어 있습니다.", null);
        }
        Instant expiry = clock.instant().plusSeconds(response.expiresIn());
        cache.write(credentials.clientId(), response.accessToken(), expiry);
        log.info("Access token issued and cached. expiresAt={}", expiry);
        return new CachedToken(response.accessToken(), expiry, null);
    }

    private boolean isUsable(String token, Instant expiry) {
        return token != null && clock.instant().isBefore(expiry.minus(REFRESH_MARGIN));
    }

    private Credentials credentials() {
        return credentialStore
                .load()
                .orElseThrow(() -> new IllegalStateException(
                        "API 키가 설정되지 않았습니다. 'tossinvest-mcp setup' 을 실행하거나 jar 를 더블클릭해 키를 입력하세요."));
    }
}
