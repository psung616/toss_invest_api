package io.github.psung616.tossmcp.client.auth;

import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.client.http.TokenEndpoint;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 발급·캐시. 클라이언트당 유효 토큰은 1개이므로 불필요한 재발급을 하지 않는다.
 *
 * TODO(P2-1): 캐시 파일(cache/token.json) + FileChannel.lock() 으로 프로세스 간 공유, 동시성 테스트
 */
@Component
public class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final TokenEndpoint tokenEndpoint;
    private final CredentialStore credentialStore;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public TokenManager(TokenEndpoint tokenEndpoint, CredentialStore credentialStore, Clock clock) {
        this.tokenEndpoint = tokenEndpoint;
        this.credentialStore = credentialStore;
        this.clock = clock;
    }

    public String getAccessToken() {
        lock.lock();
        try {
            if (accessToken == null || clock.instant().isAfter(expiresAt.minus(REFRESH_MARGIN))) {
                issue();
            }
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    public void invalidate() {
        lock.lock();
        try {
            accessToken = null;
        } finally {
            lock.unlock();
        }
    }

    private void issue() {
        Credentials credentials = credentialStore
                .load()
                .orElseThrow(() -> new IllegalStateException(
                        "API 키가 설정되지 않았습니다. 'tossinvest-mcp setup' 을 실행하거나 jar 를 더블클릭해 키를 입력하세요."));
        TokenResponse response = tokenEndpoint.issue(credentials);
        if (response == null || response.accessToken() == null) {
            throw new TossApiException(0, null, "토큰 응답이 비어 있습니다.", null);
        }
        accessToken = response.accessToken();
        expiresAt = clock.instant().plusSeconds(response.expiresIn());
        log.info("Access token issued. expiresAt={}", expiresAt);
    }
}
