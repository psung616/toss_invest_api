package io.github.psung616.tossmcp.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.web.security.LoopbackViolationException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.ApplicationContextException;

class WebLauncherTest {

    private static final CredentialStore NO_KEYS = Optional::empty;
    private static final CredentialStore FAKE_KEYS = () -> Optional.of(new Credentials("fake-id", "fake-secret"));

    private final Map<String, String> saved = new HashMap<>();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

    @BeforeEach
    void saveProperties() {
        for (String key : WebLauncher.WEB_PROPERTIES.keySet()) {
            saved.put(key, System.getProperty(key));
        }
    }

    @AfterEach
    void restoreProperties() {
        saved.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void startsWithoutKeysAndPointsToBrowserKeyForm() {
        AtomicBoolean started = new AtomicBoolean();

        int code = WebLauncher.run(
                NO_KEYS,
                () -> {
                    started.set(true);
                    return null;
                },
                err,
                ctx -> {});

        assertThat(code).as("RC-003: 키가 없어도 기동").isZero();
        assertThat(started).isTrue();
        assertThat(err()).contains("브라우저 화면에서 키를 입력");
    }

    @Test
    void forcesLoopbackAddressEvenIfOverriddenBySystemProperty() {
        System.setProperty("server.address", "0.0.0.0");

        AtomicBoolean announced = new AtomicBoolean();

        int code = WebLauncher.run(FAKE_KEYS, () -> null, err, ctx -> announced.set(true));

        assertThat(code).isZero();
        assertThat(System.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(System.getProperty("spring.main.web-application-type")).isEqualTo("servlet");
        assertThat(System.getProperty("spring.ai.mcp.server.enabled")).isEqualTo("false");
        assertThat(System.getProperty("spring.ai.mcp.server.stdio")).isEqualTo("false");
        assertThat(announced).as("기동 성공 후 접속 안내").isTrue();
    }

    @Test
    void explainsPortInUse() {
        int code = WebLauncher.run(
                FAKE_KEYS,
                () -> {
                    throw new ApplicationContextException("start failed", new PortInUseException(8765));
                },
                err,
                ctx -> {});

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("8765").contains("TOSSINVEST_WEB_PORT");
    }

    @Test
    void explainsLoopbackViolation() {
        int code = WebLauncher.run(
                FAKE_KEYS,
                () -> {
                    throw new IllegalStateException("wrapped", new LoopbackViolationException("127.0.0.1 에서만 실행합니다"));
                },
                err,
                ctx -> {});

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("127.0.0.1 에서만 실행합니다");
    }

    @Test
    void otherFailuresPointToLogFile() {
        int code = WebLauncher.run(
                FAKE_KEYS,
                () -> {
                    throw new IllegalStateException("boom");
                },
                err,
                ctx -> {});

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("server.log").doesNotContain("boom");
    }
}
