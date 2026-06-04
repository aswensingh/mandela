package com.marketinghub.auth;

import com.marketinghub.tenant.Tenant;
import com.marketinghub.tenant.TenantRepository;
import com.marketinghub.tenant.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seeds two demo tenants (Acme, Beta), their login accounts, and a few thousand fake customers
 * so a freshly-provisioned local stack is immediately usable. Mirrors {@link PlatformAdminBootstrap}:
 * an idempotent {@link ApplicationRunner} rather than a Flyway migration, so the demo data stays
 * out of the test suite and any future production run by default. Toggle with DEMO_SEED_ENABLED.
 *
 * Order relative to the platform-admin bootstrap doesn't matter — the two seed independent rows
 * (the admin has no tenant; this creates tenants + their users). Idempotent: if the 'acme' user
 * already exists the whole run is a no-op, so restarts don't duplicate anything.
 */
@Component
@Order(100)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final int CUSTOMERS_PER_TENANT = 2000;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public DemoDataSeeder(
        TenantRepository tenantRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate,
        @Value("${demo-seed.enabled:false}") boolean enabled
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (userRepository.findByUsername("acme").isPresent()) {
            log.info("Demo data already present — skipping demo seed");
            return;
        }

        Tenant acme = createTenant("Acme Dental", "healthcare", "PHN-987654321");
        Tenant beta = createTenant("Beta Bistro", "restaurant", null);

        // Passwords kept simple for local dev — these are not secrets.
        createUser("acme", "acme12345", "Acme Admin", UserRole.TENANT_ADMIN, acme.getId());
        createUser("agent1", "agent12345", "Acme Agent", UserRole.AGENT, acme.getId());
        createUser("beta", "beta12345", "Beta Admin", UserRole.TENANT_ADMIN, beta.getId());

        // Distinct phone prefixes per tenant so the two demo sets never collide.
        seedCustomers(acme.getId(), "+1555");
        seedCustomers(beta.getId(), "+1556");

        log.info(
            "Seeded demo data: tenants Acme/Beta, users acme/agent1/beta, {} customers each",
            CUSTOMERS_PER_TENANT);
    }

    private Tenant createTenant(String name, String industry, String whatsappPhoneNumberId) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setIndustry(industry);
        tenant.setStatus(TenantStatus.ACTIVE);
        if (whatsappPhoneNumberId != null) {
            tenant.setWhatsappPhoneNumberId(whatsappPhoneNumberId);
        }
        return tenantRepository.save(tenant);
    }

    private void createUser(String username, String password, String fullName, UserRole role, UUID tenantId) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setTenantId(tenantId);
        userRepository.save(user);
    }

    /**
     * Bulk-inserts CUSTOMERS_PER_TENANT rows in one round-trip via generate_series. phone_e164 is
     * unique per tenant (UNIQUE (tenant_id, phone_e164)); the {@code phonePrefix} keeps tenants apart.
     */
    private void seedCustomers(UUID tenantId, String phonePrefix) {
        jdbcTemplate.update(
            """
            INSERT INTO customers (tenant_id, phone_e164, full_name, opt_in_status)
            SELECT ?, ? || lpad(gs::text, 7, '0'), 'Demo Customer ' || gs,
                   CASE WHEN gs % 5 = 0 THEN 'OPTED_OUT'
                        WHEN gs % 2 = 0 THEN 'OPTED_IN'
                        ELSE 'UNKNOWN' END
            FROM generate_series(1, ?) AS gs
            """,
            tenantId, phonePrefix, CUSTOMERS_PER_TENANT);
    }
}
