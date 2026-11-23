package io.github.psung616.tossmcp.secret;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OS 기본 명령(security, secret-tool)을 실행하는 공통 코드. 비밀값은 명령행 인자가 아니라 stdin 으로만 넘긴다
 * (명령행 인자는 다른 프로세스에서 보일 수 있기 때문).
 */
final class CliKeyringSupport {

    private CliKeyringSupport() {}

    record Result(int exitCode, String stdout) {}

    static Result run(List<String> command, byte[] stdin) {
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(false).start();
            try (OutputStream in = process.getOutputStream()) {
                if (stdin != null) {
                    in.write(stdin);
                }
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(command.get(0) + " 명령이 응답하지 않습니다.");
            }
            return new Result(process.exitValue(), out);
        } catch (IOException e) {
            throw new IllegalStateException(command.get(0) + " 명령을 실행할 수 없습니다: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("중단되었습니다.", e);
        }
    }
}
