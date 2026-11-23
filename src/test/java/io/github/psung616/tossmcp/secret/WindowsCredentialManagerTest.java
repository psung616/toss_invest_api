package io.github.psung616.tossmcp.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * 실제 Windows 자격 증명 관리자에 테스트 전용 이름으로 가짜 값을 썼다가 지운다.
 */
@EnabledOnOs(OS.WINDOWS)
class WindowsCredentialManagerTest {

    @Test
    void writeReadDeleteRoundTrip() {
        WindowsCredentialManager manager = new WindowsCredentialManager("tossinvest-mcp-test-" + UUID.randomUUID());
        try {
            assertThat(manager.read()).isEmpty();

            manager.write("fake-client-id", "fake-secret-한글-✓".toCharArray());

            assertThat(manager.read()).contains(new Credentials("fake-client-id", "fake-secret-한글-✓"));

            manager.write("fake-client-id-2", "second".toCharArray());
            assertThat(manager.read()).contains(new Credentials("fake-client-id-2", "second"));
        } finally {
            manager.delete();
        }
        assertThat(manager.read()).isEmpty();
        manager.delete(); // 없는 항목 삭제도 예외 없음
    }
}
