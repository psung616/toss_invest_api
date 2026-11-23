package io.github.psung616.tossmcp.order;

import java.util.List;

public class GuardrailViolationException extends RuntimeException {

    private final List<String> violations;

    public GuardrailViolationException(List<String> violations) {
        super(String.join(" / ", violations));
        this.violations = List.copyOf(violations);
    }

    public GuardrailViolationException(String violation) {
        this(List.of(violation));
    }

    public List<String> violations() {
        return violations;
    }
}
