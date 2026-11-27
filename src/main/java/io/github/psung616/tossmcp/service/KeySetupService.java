package io.github.psung616.tossmcp.service;

import io.github.psung616.tossmcp.client.auth.TokenManager;
import io.github.psung616.tossmcp.client.error.TossApiException;
import io.github.psung616.tossmcp.secret.EnvCredentialStore;
import io.github.psung616.tossmcp.secret.KeyringCredentialStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * web 화면에서 받은 키를 저장하고 연결을 확인한다 (RC-003, WEB_SECURITY WS-KEY). 저장 위치는 {@code setup} 과 같은 OS 자격 증명 저장소뿐이다.
 *
 * <ul>
 *   <li>키 값은 결과·로그 어디에도 넣지 않는다. 로그에는 결과 종류만 남긴다
 *   <li>환경변수 키가 있으면 그쪽이 우선이라 저장해도 쓰이지 않으므로 거부한다
 *   <li>저장 시도는 1분에 {@value #MAX_ATTEMPTS_PER_MINUTE}회까지 (연결 테스트가 토스 토큰 API 를 부른다)
 *   <li>저장 후 이전 토큰을 버리고 새 키로 토큰을 발급한다 — 발급 성공이 곧 연결 테스트
 * </ul>
 */
@Service
public class KeySetupService {

    private static final Logger log = LoggerFactory.getLogger(KeySetupService.class);

    static final int MAX_ATTEMPTS_PER_MINUTE = 5;
    static final int MAX_CLIENT_ID_LENGTH = 200;
    static final int MAX_SECRET_LENGTH = 500;

    public enum Outcome {
        CONNECTED,
        SAVED_NOT_CONNECTED,
        INVALID_INPUT,
        ENV_KEYS_IN_USE,
        TOO_MANY_ATTEMPTS,
        SAVE_FAILED
    }

    public record Result(Outcome outcome, String message) {

        public boolean saved() {
            return outcome == Outcome.CONNECTED || outcome == Outcome.SAVED_NOT_CONNECTED;
        }
    }

    private final KeyringCredentialStore keyring;
    private final TokenManager tokens;
    private final Clock clock;
    private final BooleanSupplier envKeysPresent;
    private final Deque<Instant> attempts = new ArrayDeque<>();

    @Autowired
    public KeySetupService(KeyringCredentialStore keyring, TokenManager tokens, Clock clock) {
        this(keyring, tokens, clock, KeySetupService::envKeysPresent);
    }

    KeySetupService(KeyringCredentialStore keyring, TokenManager tokens, Clock clock, BooleanSupplier envKeysPresent) {
        this.keyring = keyring;
        this.tokens = tokens;
        this.clock = clock;
        this.envKeysPresent = envKeysPresent;
    }

    /** 환경변수 {@code TOSSINVEST_CLIENT_ID}·{@code TOSSINVEST_CLIENT_SECRET} 가 설정돼 있으면 그 키가 우선 쓰인다. */
    public static boolean envKeysPresent() {
        return new EnvCredentialStore().load().isPresent();
    }

    /**
     * 키를 저장하고 연결을 확인한다. 성공·실패와 상관없이 {@code clientSecret} 배열은 0 으로 지운다.
     */
    public synchronized Result save(String clientId, char[] clientSecret) {
        char[] secret = null;
        try {
            if (!tryAcquire()) {
                return result(Outcome.TOO_MANY_ATTEMPTS, "저장 시도가 너무 많습니다. 1분 뒤에 다시 시도하세요.");
            }
            if (envKeysPresent.getAsBoolean()) {
                return result(
                        Outcome.ENV_KEYS_IN_USE,
                        "환경변수(TOSSINVEST_CLIENT_ID·TOSSINVEST_CLIENT_SECRET)에 키가 설정돼 있어 그 키가 우선 쓰입니다. "
                                + "화면에서 저장하려면 환경변수를 먼저 지우세요.");
            }
            String id = clientId == null ? "" : clientId.strip();
            secret = strip(clientSecret);
            String problem = validate(id, secret);
            if (problem != null) {
                return result(Outcome.INVALID_INPUT, problem);
            }
            try {
                keyring.save(id, secret); // secret 배열을 지운다
            } catch (RuntimeException e) {
                log.warn("API 키 저장 실패: {}", e.getClass().getSimpleName());
                return result(Outcome.SAVE_FAILED, "이 PC 의 자격 증명 저장소에 키를 저장하지 못했습니다.");
            }
            return connect();
        } finally {
            if (clientSecret != null) {
                Arrays.fill(clientSecret, '\0');
            }
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
    }

    private Result connect() {
        try {
            tokens.reissueAfterKeyChange();
            return result(Outcome.CONNECTED, "키를 저장했고 토스증권 연결에 성공했습니다.");
        } catch (TossApiException e) {
            return result(Outcome.SAVED_NOT_CONNECTED, "키는 저장했지만 연결 테스트에 실패했습니다: " + e.toUserMessage());
        } catch (ResourceAccessException e) {
            return result(Outcome.SAVED_NOT_CONNECTED, "키는 저장했지만 토스증권 서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요.");
        } catch (RuntimeException e) {
            log.warn("연결 테스트 실패: {}", e.getClass().getSimpleName());
            return result(Outcome.SAVED_NOT_CONNECTED, "키는 저장했지만 연결 테스트에 실패했습니다. 로그 폴더의 server.log 를 확인하세요.");
        }
    }

    private Result result(Outcome outcome, String message) {
        log.info("web 키 저장 요청 결과: {}", outcome);
        return new Result(outcome, message);
    }

    static String validate(String clientId, char[] secret) {
        if (clientId.isEmpty() || secret.length == 0) {
            return "Client ID 와 Client Secret 을 모두 입력하세요.";
        }
        if (clientId.length() > MAX_CLIENT_ID_LENGTH || secret.length > MAX_SECRET_LENGTH) {
            return "입력한 값이 너무 깁니다. 토스증권에서 받은 값을 그대로 붙여넣었는지 확인하세요.";
        }
        if (!isVisibleAscii(clientId.toCharArray()) || !isVisibleAscii(secret)) {
            return "공백이나 허용되지 않는 문자가 들어 있습니다. 토스증권에서 받은 값을 그대로 붙여넣었는지 확인하세요.";
        }
        return null;
    }

    private static boolean isVisibleAscii(char[] value) {
        for (char c : value) {
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /** 앞뒤 공백을 뺀 새 배열 (붙여넣기 시 딸려 오는 줄바꿈 대비). */
    private static char[] strip(char[] value) {
        if (value == null) {
            return new char[0];
        }
        int start = 0;
        int end = value.length;
        while (start < end && Character.isWhitespace(value[start])) {
            start++;
        }
        while (end > start && Character.isWhitespace(value[end - 1])) {
            end--;
        }
        return Arrays.copyOfRange(value, start, end);
    }

    private boolean tryAcquire() {
        Instant now = clock.instant();
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(now.minus(Duration.ofMinutes(1)))) {
            attempts.pollFirst();
        }
        if (attempts.size() >= MAX_ATTEMPTS_PER_MINUTE) {
            return false;
        }
        attempts.addLast(now);
        return true;
    }
}
