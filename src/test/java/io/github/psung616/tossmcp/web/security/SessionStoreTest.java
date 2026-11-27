package io.github.psung616.tossmcp.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** WEB_SECURITY WS-SES-1~4. */
class SessionStoreTest {

    private final MutableClock clock = new MutableClock();
    private final SessionStore store = new SessionStore(clock);

    @Test
    void tokenIs256BitBase64Url() {
        assertThat(store.create()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void validTokenAccepted() {
        String token = store.create();
        assertThat(store.isValid(token)).isTrue();
        assertThat(store.isValid("other")).isFalse();
        assertThat(store.isValid(null)).isFalse();
    }

    @Test
    void onlyOneSessionAtATime() {
        String first = store.create();
        String second = store.create();
        assertThat(store.isValid(first)).isFalse();
        assertThat(store.isValid(second)).isTrue();
    }

    @Test
    void idleTimeoutEightHours() {
        String token = store.create();
        clock.advance(Duration.ofHours(8).minusSeconds(1));
        assertThat(store.isValid(token)).isTrue();
        clock.advance(Duration.ofHours(8));
        assertThat(store.isValid(token)).isFalse();
        assertThat(store.isValid(token)).as("만료 후에는 다시 살아나지 않는다").isFalse();
    }

    @Test
    void absoluteTimeoutTwentyFourHoursEvenIfActive() {
        String token = store.create();
        for (int i = 0; i < 23; i++) {
            clock.advance(Duration.ofHours(1));
            assertThat(store.isValid(token)).isTrue();
        }
        clock.advance(Duration.ofHours(1));
        assertThat(store.isValid(token)).isFalse();
    }
}
