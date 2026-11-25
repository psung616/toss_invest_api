package io.github.psung616.tossmcp.cli;

import io.github.psung616.tossmcp.cli.SetupSteps.Outcome;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * setup 의 단계 순서(입력 확인 → 저장 → 연결 테스트 → Claude Code 등록)를 한 곳에서 정한다. 콘솔·창 마법사가 같이 쓴다.
 *
 * <p>규칙: <b>앞 단계가 실패하면 다음 단계로 가지 않고</b>, 실패했을 때는 "완료"를 표시하지 않는다 (DEF-001).
 */
final class SetupFlow {

    static final String DONE = "완료! 새 Claude Code 세션에서 \"삼성전자 현재가 알려줘\" 라고 물어보세요. 이 창은 닫아도 됩니다.";

    /** 실제 구현은 {@link SetupSteps}. 테스트에서는 가짜로 바꾼다. */
    interface Steps {
        boolean hasSavedKey();

        Outcome save(String clientId, char[] clientSecret);

        Outcome testConnection();

        Outcome registerClaudeCode(Path claude);
    }

    /** 최종 결과와 마지막에 보여줄 한 줄 요약. */
    record Result(boolean ok, String summary) {}

    private final Steps steps;

    SetupFlow(Steps steps) {
        this.steps = steps;
    }

    /**
     * @param clientId       입력한 Client ID (비어 있으면 저장된 키로 연결 테스트만)
     * @param clientSecret   입력한 Secret. 이 메서드는 지우지 않으므로 호출한 쪽이 사용 후 0 으로 덮어쓴다
     * @param registerTarget 연결 테스트 성공 후에만 호출된다. 등록할 claude 경로, 등록하지 않으면 빈 값
     * @param progress       진행 상황 한 줄씩
     */
    Result run(
            String clientId, char[] clientSecret, Supplier<Optional<Path>> registerTarget, Consumer<String> progress) {
        boolean hasId = clientId != null && !clientId.isBlank();
        boolean hasSecret = clientSecret != null && clientSecret.length > 0;

        if (hasId || hasSecret) {
            if (!hasId || !hasSecret) {
                return fail(progress, "Client ID 와 Client Secret 을 모두 입력하세요.");
            }
            Outcome saved = steps.save(clientId.strip(), clientSecret);
            progress.accept(line(saved));
            if (!saved.ok()) {
                return new Result(false, "키를 저장하지 못했습니다. 위 안내를 확인한 뒤 다시 시도하세요.");
            }
        } else if (!steps.hasSavedKey()) {
            return fail(progress, "Client ID 와 Client Secret 을 입력하세요. (토스증권 WTS > 설정 > Open API 에서 발급)");
        }

        progress.accept("연결 테스트 중...");
        Outcome tested = steps.testConnection();
        progress.accept(line(tested));
        if (!tested.ok()) {
            return new Result(false, "연결 테스트에 실패해 Claude Code 에 등록하지 않았습니다. 위 안내를 확인한 뒤 다시 시도하세요.");
        }

        Optional<Path> claude = registerTarget.get();
        if (claude.isPresent()) {
            progress.accept("Claude Code 에 등록 중... (10초 정도 걸릴 수 있습니다)");
            Outcome registered = steps.registerClaudeCode(claude.get());
            progress.accept(line(registered));
            if (!registered.ok()) {
                return new Result(false, "키 저장과 연결은 성공했지만 Claude Code 등록에 실패했습니다. 위 안내를 확인하세요.");
            }
            return new Result(true, DONE);
        }
        return new Result(true, "키 저장과 연결 테스트가 끝났습니다. (Claude Code 자동 등록은 하지 않았습니다)");
    }

    private static Result fail(Consumer<String> progress, String message) {
        progress.accept("✖ " + message);
        return new Result(false, "설정이 끝나지 않았습니다.");
    }

    static String line(Outcome outcome) {
        return (outcome.ok() ? "✔ " : "✖ ") + outcome.message();
    }
}
