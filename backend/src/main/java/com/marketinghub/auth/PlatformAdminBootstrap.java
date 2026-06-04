package com.marketinghub.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public PlatformAdminBootstrap(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${platform-admin.username:}") String adminUsername,
        @Value("${platform-admin.password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(UserRole.PLATFORM_ADMIN)) {
            log.info("Platform admin already present — skipping bootstrap");
            return;
        }
        if (adminUsername == null || adminUsername.isBlank()
            || adminPassword == null || adminPassword.isBlank()) {
            log.warn(
                "No platform admin exists and PLATFORM_ADMIN_USERNAME / PLATFORM_ADMIN_PASSWORD "
                    + "are unset — cannot bootstrap. Set them in your .env to enable login."
            );
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName("Platform Admin");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setTenantId(null);
        userRepository.save(admin);
        log.info("Bootstrapped platform admin: {}", adminUsername);
    }
}
