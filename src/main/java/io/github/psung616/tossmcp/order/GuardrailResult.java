package io.github.psung616.tossmcp.order;

import java.util.List;

/** 가드레일 검사 결과. 첫 위반에서 멈추지 않고 위반 사유를 전부 담는다. */
public record GuardrailResult(boolean allowed, List<String> violations) {

    public GuardrailResult {
        violations = List.copyOf(violations);
    }

    public static GuardrailResult of(List<String> violations) {
        return new GuardrailResult(violations.isEmpty(), violations);
    }
}
