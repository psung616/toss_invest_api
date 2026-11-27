package io.github.psung616.tossmcp.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * 일회용 접속 코드 (WEB_SECURITY WS-AUTH). "콘솔을 볼 수 있는 사람 = 나" 를 로그인 대신 쓴다.
 *
 * <ul>
 *   <li>256비트 {@link SecureRandom}, Base64URL(패딩 없음, 43자)
 *   <li>1회용, 2분 만료, 유효한 코드는 동시에 하나뿐 (새로 발급하면 이전 코드 무효)
 *   <li>상수 시간 비교, 틀린 코드 10번이면 현재 코드 무효 — 새 코드는 콘솔 Enter 로만
 * </ul>
 *
 * 코드 값은 로그에 남기지 않는다. 메모리에만 있고 서버를 끄면 사라진다.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LaunchCodeStore {

    static final Duration TTL = Duration.ofMinutes(2);
    static final int MAX_FAILURES = 10;

    public enum Result {
        ACCEPTED,
        REJECTED,
        /** 이번 실패로 실패 횟수가 한도에 닿아 현재 코드를 무효로 했다. */
        REVOKED_AFTER_FAILURES
    }

    private static final Logger log = LoggerFactory.getLogger(LaunchCodeStore.class);

    private final Clock clock;
    private final SecureRandom random;

    private byte[] current;
    private Instant expiresAt;
    private int failures;

    @Autowired
    public LaunchCodeStore(Clock clock) {
        this(clock, new SecureRandom());
    }

    LaunchCodeStore(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    /** 새 코드를 만들고 이전 코드는 무효로 한다. */
    public synchronized String issue() {
        String code = Tokens.random256(random);
        current = code.getBytes(StandardCharsets.US_ASCII);
        expiresAt = clock.instant().plus(TTL);
        failures = 0;
        return code;
    }

    public synchronized Result redeem(String candidate) {
        if (current == null) {
            return Result.REJECTED;
        }
        if (!clock.instant().isBefore(expiresAt)) {
            current = null;
            return Result.REJECTED;
        }
        if (candidate != null && MessageDigest.isEqual(current, candidate.getBytes(StandardCharsets.US_ASCII))) {
            current = null;
            return Result.ACCEPTED;
        }
        failures++;
        if (failures >= MAX_FAILURES) {
            current = null;
            log.warn(
                    "틀린 접속 코드가 {}번 들어와 현재 접속 코드를 무효로 했습니다. 본인이 아니라면 다른 프로그램이 접속을 시도한 것입니다. "
                            + "새 코드가 필요하면 콘솔에서 Enter 를 누르세요.",
                    MAX_FAILURES);
            return Result.REVOKED_AFTER_FAILURES;
        }
        return Result.REJECTED;
    }

    /** 256비트 랜덤 토큰. 세션 저장소와 같이 쓴다. */
    static final class Tokens {
        private Tokens() {}

        static String random256(SecureRandom random) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
    }
}
