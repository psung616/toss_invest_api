package io.github.psung616.tossmcp.secret;

import java.util.Locale;
import java.util.Optional;

/**
 * OS 자격 증명 저장소 한 칸. 서비스명 {@value #SERVICE} 아래에 계정(Client ID)과 비밀값(Client Secret)을 둔다.
 */
public interface OsKeyring {

    String SERVICE = "tossinvest-mcp";

    Optional<Credentials> read();

    /** 같은 서비스명의 기존 값을 덮어쓴다. 호출 후 secret 배열은 호출자가 0으로 지운다. */
    void write(String clientId, char[] clientSecret);

    void delete();

    static OsKeyring detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new WindowsCredentialManager(SERVICE);
        }
        if (os.contains("mac")) {
            return new MacKeychain(SERVICE);
        }
        return new LinuxSecretTool(SERVICE);
    }
}
