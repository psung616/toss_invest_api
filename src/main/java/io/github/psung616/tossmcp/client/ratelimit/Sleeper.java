package io.github.psung616.tossmcp.client.ratelimit;

import java.time.Duration;

/** 대기. 테스트에서 실제로 기다리지 않도록 주입한다. */
@FunctionalInterface
public interface Sleeper {

    /** 기다리지 않음 (테스트용). */
    Sleeper NONE = d -> {};

    void sleep(Duration duration);

    static Sleeper system() {
        return d -> {
            try {
                Thread.sleep(d);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("대기 중 중단되었습니다.", e);
            }
        };
    }
}
