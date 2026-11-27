package io.github.psung616.tossmcp.web.security;

import static io.github.psung616.tossmcp.web.security.LaunchCodeStore.Result.ACCEPTED;
import static io.github.psung616.tossmcp.web.security.LaunchCodeStore.Result.REJECTED;
import static io.github.psung616.tossmcp.web.security.LaunchCodeStore.Result.REVOKED_AFTER_FAILURES;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** WEB_SECURITY WS-AUTH-1, 3~5. */
class LaunchCodeStoreTest {

    private final MutableClock clock = new MutableClock();
    private final LaunchCodeStore store = new LaunchCodeStore(clock);

    @Test
    void codeIs256BitBase64Url() {
        String code = store.issue();
        assertThat(code).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(store.issue()).isNotEqualTo(code);
    }

    @Test
    void codeIsSingleUse() {
        String code = store.issue();
        assertThat(store.redeem(code)).isEqualTo(ACCEPTED);
        assertThat(store.redeem(code)).isEqualTo(REJECTED);
    }

    @Test
    void codeExpiresAfterTwoMinutes() {
        String code = store.issue();
        clock.advance(Duration.ofMinutes(2));
        assertThat(store.redeem(code)).isEqualTo(REJECTED);
    }

    @Test
    void codeValidJustBeforeExpiry() {
        String code = store.issue();
        clock.advance(Duration.ofSeconds(119));
        assertThat(store.redeem(code)).isEqualTo(ACCEPTED);
    }

    @Test
    void newCodeInvalidatesPreviousOne() {
        String first = store.issue();
        String second = store.issue();
        assertThat(store.redeem(first)).isEqualTo(REJECTED);
        assertThat(store.redeem(second)).isEqualTo(ACCEPTED);
    }

    @Test
    void wrongOrMissingCodeIsRejected() {
        store.issue();
        assertThat(store.redeem("wrong")).isEqualTo(REJECTED);
        assertThat(store.redeem(null)).isEqualTo(REJECTED);
        assertThat(store.redeem("")).isEqualTo(REJECTED);
    }

    @Test
    void nothingIssuedMeansNothingAccepted() {
        assertThat(store.redeem("anything")).isEqualTo(REJECTED);
    }

    @Test
    void tenFailuresRevokeTheCurrentCode() {
        String code = store.issue();
        for (int i = 1; i < LaunchCodeStore.MAX_FAILURES; i++) {
            assertThat(store.redeem("wrong-" + i)).isEqualTo(REJECTED);
        }
        assertThat(store.redeem("wrong-last")).isEqualTo(REVOKED_AFTER_FAILURES);
        assertThat(store.redeem(code)).isEqualTo(REJECTED);
    }

    @Test
    void issuingResetsFailureCount() {
        store.issue();
        for (int i = 1; i < LaunchCodeStore.MAX_FAILURES; i++) {
            store.redeem("wrong-" + i);
        }
        String fresh = store.issue();
        assertThat(store.redeem("wrong-again")).isEqualTo(REJECTED);
        assertThat(store.redeem(fresh)).isEqualTo(ACCEPTED);
    }
}
