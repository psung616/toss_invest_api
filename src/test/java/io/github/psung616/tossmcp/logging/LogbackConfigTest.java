package io.github.psung616.tossmcp.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 실제 logback-spring.xml 을 별도 LoggerContext 로 띄워 stdout 무출력·마스킹을 확인한다. 값은 모두 가짜. */
class LogbackConfigTest {

    @TempDir
    Path logs;

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private LoggerContext context;

    @BeforeEach
    void setUp() throws Exception {
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("tossinvest.logs", logs.toString());
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback-spring.xml"));
    }

    @AfterEach
    void tearDown() {
        context.stop();
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void nothingGoesToStdoutAndSecretsAreMasked() throws Exception {
        Logger log = context.getLogger("io.github.psung616.tossmcp.Test");
        log.info("token response {\"access_token\":\"fake.jwt.value\"}");
        log.warn(
                "request failed Bearer fake.jwt.value accountSeq=7",
                new IllegalStateException("client_secret=fake-secret"));
        context.stop();

        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();

        String stderr = err.toString(StandardCharsets.UTF_8);
        assertThat(stderr).contains("request failed Bearer *** accountSeq=***");
        assertThat(stderr).doesNotContain("fake.jwt.value").doesNotContain("fake-secret");

        String file = Files.readString(logs.resolve("server.log"), StandardCharsets.UTF_8);
        assertThat(file).contains("\"access_token\":\"***\"").contains("client_secret=***");
        assertThat(file).doesNotContain("fake.jwt.value").doesNotContain("fake-secret");
    }
}
