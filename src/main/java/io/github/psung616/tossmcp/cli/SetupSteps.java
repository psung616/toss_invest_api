package io.github.psung616.tossmcp.cli;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.client.http.TokenEndpoint;
import io.github.psung616.tossmcp.config.AppPaths;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.secret.KeyringCredentialStore;
import io.github.psung616.tossmcp.secret.OsKeyring;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.client.ResourceAccessException;

/**
 * 콘솔 마법사와 창(GUI) 마법사가 공유하는 단계: 키 저장 → 연결 테스트 → Claude Code 등록.
 */
final class SetupSteps {

    private final KeyringCredentialStore store;
    private final TokenCacheFile tokenCache;

    SetupSteps() {
        this(
                new KeyringCredentialStore(OsKeyring.detect()),
                new TokenCacheFile(AppPaths.detect().cacheDir().resolve("token.json")));
    }

    SetupSteps(KeyringCredentialStore store, TokenCacheFile tokenCache) {
        this.store = store;
        this.tokenCache = tokenCache;
    }

    record Outcome(boolean ok, String message) {}

    static String keyringName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "Windows 자격 증명 관리자";
        }
        return os.contains("mac") ? "macOS 키체인" : "Secret Service(키링)";
    }

    /** 이미 저장된 키가 있으면 마스킹된 Client ID 를 돌려준다. */
    Optional<String> savedClientIdMasked() {
        try {
            return store.load().map(c -> mask(c.clientId()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    Outcome save(String clientId, char[] clientSecret) {
        try {
            store.save(clientId, clientSecret);
            return new Outcome(true, keyringName() + "에 키를 저장했습니다.");
        } catch (RuntimeException e) {
            return new Outcome(false, e.getMessage());
        }
    }

    /**
     * 저장된 키로 토큰 발급을 시도한다. 토스증권은 클라이언트당 토큰이 1개라서, 받은 토큰을 공유 캐시에 기록해
     * 실행 중인 서버(serve·discord)가 재발급 없이 이어받게 한다.
     */
    Outcome testConnection() {
        Optional<Credentials> credentials = store.load();
        if (credentials.isEmpty()) {
            return new Outcome(false, "저장된 키가 없습니다.");
        }
        try {
            Credentials c = credentials.get();
            tokenCache.withLock(() -> {
                TokenResponse token = TokenEndpoint.standalone().issue(c);
                tokenCache.write(
                        c.clientId(), token.accessToken(), Instant.now().plusSeconds(token.expiresIn()));
                return null;
            });
            return new Outcome(true, "토스증권 연결 성공 (토큰 발급 확인)");
        } catch (TossApiException e) {
            return new Outcome(false, "연결 실패: " + e.toUserMessage());
        } catch (ResourceAccessException e) {
            return new Outcome(false, "토스증권 서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요.");
        }
    }

    Optional<Path> findClaudeCode() {
        return ClaudeCodeRegistrar.findClaude();
    }

    Outcome registerClaudeCode(Path claude) {
        try {
            ClaudeCodeRegistrar.Result r = ClaudeCodeRegistrar.register(claude);
            return new Outcome(r.ok(), r.message());
        } catch (IllegalStateException e) {
            return new Outcome(false, e.getMessage());
        }
    }

    Outcome resetKeys() {
        try {
            store.delete();
            tokenCache.withLock(() -> {
                tokenCache.delete();
                return null;
            });
            return new Outcome(true, keyringName() + "에서 키를 삭제하고 토큰 캐시를 지웠습니다.");
        } catch (RuntimeException e) {
            return new Outcome(false, e.getMessage());
        }
    }

    static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 4) + "****";
    }
}
