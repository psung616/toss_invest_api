package io.github.psung616.tossmcp.secret;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SecretConfig {

    @Bean
    OsKeyring osKeyring() {
        return OsKeyring.detect();
    }

    @Bean
    KeyringCredentialStore keyringCredentialStore(OsKeyring osKeyring) {
        return new KeyringCredentialStore(osKeyring);
    }

    @Bean
    @Primary
    CredentialStore credentialStore(KeyringCredentialStore keyringCredentialStore) {
        return new CompositeCredentialStore(List.of(new EnvCredentialStore(), keyringCredentialStore));
    }
}
