package io.github.psung616.tossmcp.secret;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * macOS 키체인. 기본 내장 {@code security} 명령을 사용한다.
 *
 * TODO: macOS 실기기 검증 필요 (Windows 에서만 개발·테스트됨)
 */
final class MacKeychain implements OsKeyring {

    private static final Pattern ACCOUNT = Pattern.compile("\"acct\"<blob>=\"([^\"]*)\"");

    private final String service;

    MacKeychain(String service) {
        this.service = service;
    }

    @Override
    public Optional<Credentials> read() {
        CliKeyringSupport.Result attrs =
                CliKeyringSupport.run(List.of("security", "find-generic-password", "-s", service), null);
        if (attrs.exitCode() != 0) {
            return Optional.empty();
        }
        Matcher m = ACCOUNT.matcher(attrs.stdout());
        if (!m.find()) {
            return Optional.empty();
        }
        CliKeyringSupport.Result secret =
                CliKeyringSupport.run(List.of("security", "find-generic-password", "-s", service, "-w"), null);
        if (secret.exitCode() != 0) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(m.group(1), secret.stdout().strip()));
    }

    @Override
    public void write(String clientId, char[] clientSecret) {
        // security -i : stdin 으로 명령을 받으므로 비밀값이 프로세스 목록에 노출되지 않는다.
        String command = "add-generic-password -U -s " + quote(service) + " -a " + quote(clientId) + " -w "
                + quote(new String(clientSecret)) + "\n";
        CliKeyringSupport.Result r =
                CliKeyringSupport.run(List.of("security", "-i"), command.getBytes(StandardCharsets.UTF_8));
        if (r.exitCode() != 0) {
            throw new IllegalStateException("키체인에 저장하지 못했습니다.");
        }
    }

    @Override
    public void delete() {
        CliKeyringSupport.run(List.of("security", "delete-generic-password", "-s", service), null);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
