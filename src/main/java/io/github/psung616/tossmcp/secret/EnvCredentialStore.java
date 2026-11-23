package io.github.psung616.tossmcp.secret;

import java.util.Optional;

/**
 * 환경변수 TOSSINVEST_CLIENT_ID / TOSSINVEST_CLIENT_SECRET 에서 키를 읽는다.
 * Claude Desktop 확장(.mcpb)은 사용자가 입력한 키를 이 환경변수로 넘겨준다.
 */
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
