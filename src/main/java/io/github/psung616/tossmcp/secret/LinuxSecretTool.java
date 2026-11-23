package io.github.psung616.tossmcp.secret;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux Secret Service (GNOME Keyring / KWallet). {@code secret-tool}(libsecret-tools) 명령을 사용한다.
 *
 * TODO: Linux 실기기 검증 필요 (Windows 에서만 개발·테스트됨)
 */
final class LinuxSecretTool implements OsKeyring {

    private static final Pattern ACCOUNT = Pattern.compile("attribute\\.account = (.+)");

    private final String service;

    LinuxSecretTool(String service) {
        this.service = service;
    }

    @Override
    public Optional<Credentials> read() {
        CliKeyringSupport.Result search =
                CliKeyringSupport.run(List.of("secret-tool", "search", "service", service), null);
        Matcher m = ACCOUNT.matcher(search.stdout());
        if (search.exitCode() != 0 || !m.find()) {
            return Optional.empty();
        }
        CliKeyringSupport.Result secret =
                CliKeyringSupport.run(List.of("secret-tool", "lookup", "service", service), null);
        if (secret.exitCode() != 0 || secret.stdout().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(m.group(1).strip(), secret.stdout().strip()));
    }

    @Override
    public void write(String clientId, char[] clientSecret) {
        delete();
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(clientSecret));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        CliKeyringSupport.Result r = CliKeyringSupport.run(
                List.of("secret-tool", "store", "--label=tossinvest-mcp", "service", service, "account", clientId),
                bytes);
        java.util.Arrays.fill(bytes, (byte) 0);
        if (r.exitCode() != 0) {
            throw new IllegalStateException("Secret Service 에 저장하지 못했습니다. secret-tool(libsecret-tools) 설치를 확인하세요.");
        }
    }

    @Override
    public void delete() {
        CliKeyringSupport.run(List.of("secret-tool", "clear", "service", service), null);
    }
}
