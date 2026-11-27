package io.github.psung616.tossmcp.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * 세션 (WEB_SECURITY WS-SES). 서버 메모리에만 두고, 동시에 하나만 유효하다(새 로그인 시 이전 세션 무효). 유휴 8시간·절대 24시간 만료. Spring
 * {@code HttpSession} 은 쓰지 않는다.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SessionStore {

    public static final String COOKIE = "tim_session";
    static final Duration IDLE_TIMEOUT = Duration.ofHours(8);
    static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(24);

    private final Clock clock;
    private final SecureRandom random;

    private byte[] token;
    private Instant createdAt;
    private Instant lastSeen;

    @Autowired
    public SessionStore(Clock clock) {
        this(clock, new SecureRandom());
    }

    SessionStore(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    /** 새 세션을 만들고 이전 세션은 무효로 한다. */
    public synchronized String create() {
        String value = LaunchCodeStore.Tokens.random256(random);
        token = value.getBytes(StandardCharsets.US_ASCII);
        createdAt = clock.instant();
        lastSeen = createdAt;
        return value;
    }

    /** 유효하면 마지막 사용 시각을 갱신한다. 만료됐으면 세션을 지운다. */
    public synchronized boolean isValid(String candidate) {
        if (token == null || candidate == null) {
            return false;
        }
        if (!MessageDigest.isEqual(token, candidate.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        Instant now = clock.instant();
        if (!now.isBefore(createdAt.plus(ABSOLUTE_TIMEOUT)) || !now.isBefore(lastSeen.plus(IDLE_TIMEOUT))) {
            token = null;
            return false;
        }
        lastSeen = now;
        return true;
    }
}
