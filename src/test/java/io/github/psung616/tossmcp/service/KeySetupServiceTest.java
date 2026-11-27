package io.github.psung616.tossmcp.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.auth.TokenCacheFile;
import io.github.psung616.tossmcp.client.auth.TokenIssuer;
import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.secret.KeyringCredentialStore;
import io.github.psung616.tossmcp.secret.OsKeyring;
import io.github.psung616.tossmcp.service.KeySetupService.Outcome;
import io.github.psung616.tossmcp.service.KeySetupService.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RC-003 WS-KEY: 화면 키 저장. 키·토큰은 모두 지어낸 가짜 값이다. */
class KeySetupServiceTest {

    private static final String ID = "fake-client-id";
    private static final String SECRET = "fake-client-secret-123";

    @TempDir
    Path dir;

    /** 메모리 저장소. 실제 OS 자격 증명 저장소는 건드리지 않는다. */
    static final class MemoryKeyring implements OsKeyring {
        Credentials stored;
        boolean failWrites;

        @Override
        public Optional<Credentials> read() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(String clientId, char[] clientSecret) {
            if (failWrites) {
                throw new IllegalStateException("CredWriteW failed: " + new String(clientSecret));
            }
            stored = new Credentials(clientId, new String(clientSecret));
        }

        @Override
        public void delete() {
            stored = null;
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-27T01:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MemoryKeyring keyring = new MemoryKeyring();
    private final MutableClock clock = new MutableClock();
    private final AtomicInteger issued = new AtomicInteger();
    private TokenIssuer issuer = c -> new TokenResponse("token-" + issued.incrementAndGet(), "Bearer", 86400);
    private boolean envKeys;

    private KeySetupService service() {
        KeyringCredentialStore store = new KeyringCredentialStore(keyring);
        TokenManager tokens =
                new TokenManager(c -> issuer.issue(c), store, new TokenCacheFile(dir.resolve("token.json")), clock);
        return new KeySetupService(store, tokens, clock, () -> envKeys);
    }

    private static void assertNoKeyIn(Result result) {
        assertThat(result.message()).doesNotContain(SECRET).doesNotContain(ID);
    }

    @Test
    void savesToKeyringThenConnects() {
        char[] secret = SECRET.toCharArray();

        Result result = service().save(ID, secret);

        assertThat(result.outcome()).isEqualTo(Outcome.CONNECTED);
        assertThat(result.saved()).isTrue();
        assertThat(keyring.stored).isEqualTo(new Credentials(ID, SECRET));
        assertThat(issued).as("연결 테스트 = 토큰 발급").hasValue(1);
        assertThat(secret).as("받은 Secret 배열은 지운다").containsOnly('\0');
        assertNoKeyIn(result);
    }

    @Test
    void stripsWhitespaceFromPastedValues() {
        service().save("  " + ID + "\n", ("\t" + SECRET + "\r\n").toCharArray());

        assertThat(keyring.stored).isEqualTo(new Credentials(ID, SECRET));
    }

    @Test
    void rejectsEmptyTooLongOrInvalidInputWithoutSaving() {
        KeySetupService service = service();
        Result empty = service.save("", SECRET.toCharArray());
        Result nullSecret = service.save(ID, null);
        Result inner = service.save("fake id", SECRET.toCharArray());
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        Result tooLong = service.save(
                ID, "x".repeat(KeySetupService.MAX_SECRET_LENGTH + 1).toCharArray());
        Result nonAscii = service.save("키", SECRET.toCharArray());

        for (Result r : new Result[] {empty, nullSecret, inner, tooLong, nonAscii}) {
            assertThat(r.outcome()).isEqualTo(Outcome.INVALID_INPUT);
            assertThat(r.saved()).isFalse();
        }
        assertThat(keyring.stored).isNull();
        assertThat(issued).hasValue(0);
    }

    @Test
    void refusesWhenEnvironmentKeysAreInUse() {
        envKeys = true;

        Result result = service().save(ID, SECRET.toCharArray());

        assertThat(result.outcome()).isEqualTo(Outcome.ENV_KEYS_IN_USE);
        assertThat(result.message()).contains("환경변수");
        assertThat(keyring.stored).isNull();
    }

    @Test
    void limitsAttemptsToFivePerMinute() {
        KeySetupService service = service();
        for (int i = 0; i < KeySetupService.MAX_ATTEMPTS_PER_MINUTE; i++) {
            assertThat(service.save(ID, SECRET.toCharArray()).outcome()).isEqualTo(Outcome.CONNECTED);
        }
        assertThat(service.save(ID, SECRET.toCharArray()).outcome()).isEqualTo(Outcome.TOO_MANY_ATTEMPTS);

        clock.now = clock.now.plus(Duration.ofMinutes(1));
        assertThat(service.save(ID, SECRET.toCharArray()).outcome()).isEqualTo(Outcome.CONNECTED);
    }

    @Test
    void savedButTossRejectsShowsUserMessage() {
        issuer = c -> {
            throw new TossApiException(401, "invalid_client", "Client authentication failed: client_id", "req-1");
        };

        Result result = service().save(ID, SECRET.toCharArray());

        assertThat(result.outcome()).isEqualTo(Outcome.SAVED_NOT_CONNECTED);
        assertThat(result.saved()).isTrue();
        assertThat(result.message()).contains("연결 테스트에 실패").contains("req-1");
        assertThat(keyring.stored).as("키는 저장됨 (setup 과 같음)").isNotNull();
        assertNoKeyIn(result);
    }

    @Test
    void replacingKeysDropsTokenOfPreviousKey() throws Exception {
        KeySetupService service = service();
        service.save(ID, SECRET.toCharArray());
        String first = Files.readString(dir.resolve("token.json"));

        service.save(ID, "fake-new-secret".toCharArray());

        assertThat(issued).hasValue(2);
        assertThat(Files.readString(dir.resolve("token.json"))).isNotEqualTo(first);
    }

    @Test
    void keyringFailureDoesNotLeakExceptionMessage() {
        keyring.failWrites = true;

        Result result = service().save(ID, SECRET.toCharArray());

        assertThat(result.outcome()).isEqualTo(Outcome.SAVE_FAILED);
        assertThat(result.saved()).isFalse();
        assertNoKeyIn(result);
        assertThat(result.message()).doesNotContain("CredWriteW");
    }
}
