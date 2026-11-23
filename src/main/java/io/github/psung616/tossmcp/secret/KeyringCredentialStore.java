package io.github.psung616.tossmcp.secret;

import java.util.Arrays;
import java.util.Optional;

/**
 * OS 자격 증명 저장소에 저장된 키. setup 이 쓰고, serve 가 읽는다.
 */
public class KeyringCredentialStore implements CredentialStore {

    private final OsKeyring keyring;

    public KeyringCredentialStore(OsKeyring keyring) {
        this.keyring = keyring;
    }

    @Override
    public Optional<Credentials> load() {
        return keyring.read();
    }

    /** 저장 후 clientSecret 배열을 0으로 지운다. */
    public void save(String clientId, char[] clientSecret) {
        try {
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("Client ID 가 비어 있습니다.");
            }
            if (clientSecret == null || clientSecret.length == 0) {
                throw new IllegalArgumentException("Client Secret 이 비어 있습니다.");
            }
            keyring.write(clientId.strip(), clientSecret);
        } finally {
            if (clientSecret != null) {
                Arrays.fill(clientSecret, '\0');
            }
        }
    }

    public void delete() {
        keyring.delete();
    }
}
