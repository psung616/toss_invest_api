package io.github.psung616.tossmcp.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.cli.SetupSteps.Outcome;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** DEF-001 회귀: 앞 단계가 실패하면 다음 단계로 가지 않고, 실패 시 "완료"를 표시하지 않는다. 값은 모두 가짜. */
class SetupFlowTest {

    private static final Path CLAUDE = Path.of("claude");

    /** 호출을 기록하는 가짜 단계. */
    static final class FakeSteps implements SetupFlow.Steps {
        final List<String> calls = new ArrayList<>();
        boolean hasSavedKey;
        Outcome saveResult = new Outcome(true, "저장함");
        Outcome testResult = new Outcome(true, "연결 성공");
        Outcome registerResult = new Outcome(true, "등록함");

        @Override
        public boolean hasSavedKey() {
            return hasSavedKey;
        }

        @Override
        public Outcome save(String clientId, char[] clientSecret) {
            calls.add("save");
            return saveResult;
        }

        @Override
        public Outcome testConnection() {
            calls.add("test");
            return testResult;
        }

        @Override
        public Outcome registerClaudeCode(Path claude) {
            calls.add("register");
            return registerResult;
        }
    }

    private final FakeSteps steps = new FakeSteps();
    private final List<String> progress = new ArrayList<>();

    private SetupFlow.Result run(String id, String secret, Optional<Path> claude) {
        return new SetupFlow(steps).run(id, secret.toCharArray(), () -> claude, progress::add);
    }

    private String output(SetupFlow.Result r) {
        return String.join("\n", progress) + "\n" + r.summary();
    }

    @Test
    void blankInputWithoutSavedKeyStopsBeforeConnectionTest() {
        SetupFlow.Result r = run("", "", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(steps.calls).isEmpty();
        assertThat(output(r)).contains("입력").doesNotContain("완료!");
    }

    @Test
    void blankInputWithSavedKeyRetestsSavedKey() {
        steps.hasSavedKey = true;
        SetupFlow.Result r = run("", "", Optional.of(CLAUDE));

        assertThat(r.ok()).isTrue();
        assertThat(steps.calls).containsExactly("test", "register");
    }

    @Test
    void onlyOneFieldIsRejectedWithoutSaving() {
        SetupFlow.Result r = run("fake-client", "", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(steps.calls).isEmpty();
        assertThat(output(r)).doesNotContain("완료!");
    }

    @Test
    void whitespaceOnlyIdIsRejected() {
        SetupFlow.Result r = run("   ", "fake-secret", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(steps.calls).isEmpty();
    }

    @Test
    void saveFailureStops() {
        steps.saveResult = new Outcome(false, "저장 실패");
        SetupFlow.Result r = run("fake-client", "fake-secret", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(steps.calls).containsExactly("save");
        assertThat(output(r)).doesNotContain("완료!");
    }

    @Test
    void connectionFailureDoesNotRegisterOrClaimSuccess() {
        steps.testResult = new Outcome(false, "저장된 키가 없습니다.");
        SetupFlow.Result r = run("fake-client", "fake-secret", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(steps.calls).containsExactly("save", "test");
        assertThat(output(r)).contains("등록하지 않았습니다").doesNotContain("완료!");
    }

    @Test
    void registerTargetIsAskedOnlyAfterConnectionSucceeds() {
        steps.testResult = new Outcome(false, "연결 실패");
        boolean[] asked = {false};
        new SetupFlow(steps)
                .run(
                        "fake-client",
                        "fake-secret".toCharArray(),
                        () -> {
                            asked[0] = true;
                            return Optional.of(CLAUDE);
                        },
                        progress::add);

        assertThat(asked[0]).isFalse();
    }

    @Test
    void fullSuccess() {
        SetupFlow.Result r = run("fake-client", "fake-secret", Optional.of(CLAUDE));

        assertThat(r.ok()).isTrue();
        assertThat(steps.calls).containsExactly("save", "test", "register");
        assertThat(r.summary()).contains("완료!");
    }

    @Test
    void successWithoutRegistration() {
        SetupFlow.Result r = run("fake-client", "fake-secret", Optional.empty());

        assertThat(r.ok()).isTrue();
        assertThat(steps.calls).containsExactly("save", "test");
    }

    @Test
    void registrationFailureIsReportedAsFailure() {
        steps.registerResult = new Outcome(false, "claude 실행 실패");
        SetupFlow.Result r = run("fake-client", "fake-secret", Optional.of(CLAUDE));

        assertThat(r.ok()).isFalse();
        assertThat(output(r)).doesNotContain("완료!");
    }
}
