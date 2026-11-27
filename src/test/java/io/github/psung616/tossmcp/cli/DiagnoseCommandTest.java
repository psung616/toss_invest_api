package io.github.psung616.tossmcp.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.psung616.tossmcp.client.dto.TokenResponse;
import io.github.psung616.tossmcp.secret.CredentialStore;
import io.github.psung616.tossmcp.secret.Credentials;
import io.github.psung616.tossmcp.service.DiagnosticsService;
import io.github.psung616.tossmcp.service.DiagnosticsServiceTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** P3-5 완료 기준: diagnose 출력에 키·토큰·accountSeq·금액·보유 정보·사용자 이름이 절대 없다. 값은 모두 지어낸 것. */
class DiagnoseCommandTest {

    private static final String CLIENT_ID = "fake-client-id-ABC123";
    private static final String SECRET = "fake-client-secret-XYZ789";
    private static final String TOKEN = "fake-access-token-QWE456";

    @TempDir
    Path dir;

    @Test
    void outputHasLabelsButNoSecretsAccountOrAmounts() throws IOException {
        CredentialStore keys = () -> Optional.of(new Credentials(CLIENT_ID, SECRET));
        DiagnosticsService service =
                DiagnosticsServiceTestSupport.service(dir, keys, c -> new TokenResponse(TOKEN, "Bearer", 86400));
        Path logs = dir.resolve("logs");
        Files.writeString(
                logs.resolve("server.log"),
                "2026-11-27T10:00:00.000+09:00  WARN [main] x : 키 " + CLIENT_ID + " " + SECRET + " Bearer " + TOKEN
                        + " accountSeq=987654 accountNo=000-00-111111 cashBuyingPower=1234567 holdings 005930 10주\n"
                        + "2026-11-27T10:00:01.000+09:00  WARN [main] x : Toss API error status=403 code=ip-not-allowed requestId=r-1\n");
        Files.writeString(
                logs.resolve("audit.jsonl"), "{\"symbol\":\"005930\",\"quantity\":\"10\",\"price\":\"71000\"}\n");

        String out = DiagnoseCommand.format(service.report(true));

        assertThat(out)
                .contains("버전: 0.1.0")
                .contains("키 설정: 예")
                .contains("연결 테스트: 성공")
                .contains("code=ip-not-allowed requestId=r-1")
                .contains("주문 기능: 꺼짐, DRY_RUN: 예")
                .contains("감사로그 이벤트 수: 1")
                .contains("web 포트: 8765");
        for (String banned : new String[] {
            CLIENT_ID,
            SECRET,
            TOKEN,
            "987654",
            "111111",
            "1234567",
            "71000",
            "005930",
            "10주",
            System.getProperty("user.name")
        }) {
            assertThat(out).as("diagnose 출력에 %s", banned).doesNotContain(banned);
        }
    }

    @Test
    void outputWithoutKeys() {
        DiagnosticsService service = DiagnosticsServiceTestSupport.service(dir, Optional::empty, c -> {
            throw new AssertionError("키 없으면 연결 시도 안 함");
        });

        String out = DiagnoseCommand.format(service.report(true));

        assertThat(out).contains("키 설정: 아니오").contains("연결 테스트: 건너뜀 (키 없음)").contains("최근 토스 API 에러 (최대 5개): 없음");
    }
}
