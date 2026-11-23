package io.github.psung616.tossmcp.secret;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 환경변수 TOSSINVEST_CLIENT_ID / TOSSINVEST_CLIENT_SECRET 에서 키를 읽는다.
 *
 * TODO(P1-3): KeyringCredentialStore + CompositeCredentialStore, setup / reset-keys 명령
 */
@Component
public class EnvCredentialStore implements CredentialStore {

    @Override
    public Optional<Credentials> load() {
        String id = System.getenv("TOSSINVEST_CLIENT_ID");
        String secret = System.getenv("TOSSINVEST_CLIENT_SECRET");
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(id, secret));
    }
}
