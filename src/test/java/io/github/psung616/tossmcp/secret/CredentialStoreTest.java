package io.github.psung616.tossmcp.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CredentialStoreTest {

    /** 메모리 가짜 키링. */
    static final class FakeKeyring implements OsKeyring {
        Credentials stored;
        char[] lastSecretArray;

        @Override
        public Optional<Credentials> read() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void write(String clientId, char[] clientSecret) {
            lastSecretArray = clientSecret;
            stored = new Credentials(clientId, new String(clientSecret));
        }

        @Override
        public void delete() {
            stored = null;
        }
    }

    @Test
    void savesAndLoadsAndWipesSecretArray() {
        FakeKeyring keyring = new FakeKeyring();
        KeyringCredentialStore store = new KeyringCredentialStore(keyring);
        char[] secret = "fake-secret-value".toCharArray();

        store.save("  fake-id  ", secret);

        assertThat(store.load()).contains(new Credentials("fake-id", "fake-secret-value"));
        assertThat(secret).containsOnly('\0');
    }

    @Test
    void rejectsBlankInputAndStillWipes() {
        KeyringCredentialStore store = new KeyringCredentialStore(new FakeKeyring());
        char[] secret = "x".toCharArray();

        assertThatThrownBy(() -> store.save(" ", secret)).isInstanceOf(IllegalArgumentException.class);
        assertThat(secret).containsOnly('\0');
    }

    @Test
    void compositeUsesFirstStoreThatHasKeys() {
        CredentialStore empty = Optional::empty;
        CredentialStore first = () -> Optional.of(new Credentials("first", "s1"));
        CredentialStore second = () -> Optional.of(new Credentials("second", "s2"));

        assertThat(new CompositeCredentialStore(List.of(empty, first, second)).load())
                .map(Credentials::clientId)
                .contains("first");
        assertThat(new CompositeCredentialStore(List.of(empty)).load()).isEmpty();
    }

    @Test
    void credentialsToStringHidesValues() {
        assertThat(new Credentials("fake-id", "fake-secret").toString())
                .doesNotContain("fake-id")
                .doesNotContain("fake-secret");
    }
}
