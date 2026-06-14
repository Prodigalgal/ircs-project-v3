package com.prodigalgal.ircs.notification.mail;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MailCredentialResolver {

    private final NotificationMailProperties properties;
    private final CredentialServiceMailCredentialRepository repository;

    Optional<MailCredential> resolve() {
        NotificationMailProperties.CredentialProvider provider = properties.getCredentialProvider();
        if (provider == NotificationMailProperties.CredentialProvider.CREDENTIAL_SERVICE) {
            return Optional.of(repository.leaseRequired());
        }
        return Optional.empty();
    }
}
