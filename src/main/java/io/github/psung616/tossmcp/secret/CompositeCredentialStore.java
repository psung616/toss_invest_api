package io.github.psung616.tossmcp.secret;

import java.util.List;
import java.util.Optional;

/**
 * 앞에서부터 차례로 찾는다. 기본 순서: 환경변수(.mcpb 설정·고급 사용자) → OS 자격 증명 저장소(setup).
 */
public class CompositeCredentialStore implements CredentialStore {

    private final List<CredentialStore> stores;

    public CompositeCredentialStore(List<CredentialStore> stores) {
        this.stores = List.copyOf(stores);
    }

    @Override
    public Optional<Credentials> load() {
        for (CredentialStore store : stores) {
            Optional<Credentials> found = store.load();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
