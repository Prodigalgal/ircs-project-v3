package com.prodigalgal.ircs.identity.security;



import com.prodigalgal.ircs.identity.domain.IdentityConfigKey;
import com.prodigalgal.ircs.identity.application.IdentityConfigService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
class JwtSecretBootstrapper implements ApplicationRunner {

    private static final int SECRET_BYTES = 48;

    private final IdentityConfigService configService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void run(ApplicationArguments args) {
        if (configService.hasInjectedValue(IdentityConfigKey.JWT_SECRET)) {
            log.info("JWT secret is provided by runtime injection; skipping startup secret generation");
            return;
        }

        if (StringUtils.hasText(configService.storedValue(IdentityConfigKey.JWT_SECRET))) {
            log.info("JWT secret already exists in system config; skipping startup secret generation");
            return;
        }

        String secret = generateSecret();
        configService.installRuntimeValues(Map.of(IdentityConfigKey.JWT_SECRET, secret));
        configService.updateValue(IdentityConfigKey.JWT_SECRET, secret);
        log.warn("Generated initial runtime JWT secret because no injected or stored JWT secret was found");
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
