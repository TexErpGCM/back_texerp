package co.texerp.integrations.config;

import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Permission;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.repository.RoleRepository;
import co.texerp.integrations.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataInitializer {

    @Value("${app.seed.admin-name}") private String adminName;
    @Value("${app.seed.admin-email}") private String adminEmail;
    @Value("${app.seed.admin-password}") private String adminPassword;
    @Value("${app.seed.analyst-name}") private String analystName;
    @Value("${app.seed.analyst-email}") private String analystEmail;
    @Value("${app.seed.analyst-password}") private String analystPassword;
    @Value("${app.seed.seller-name}") private String sellerName;
    @Value("${app.seed.seller-email}") private String sellerEmail;
    @Value("${app.seed.seller-password}") private String sellerPassword;

    @Bean
    CommandLineRunner seed(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        return args -> initialize(users, roles, encoder);
    }

    @Transactional
    void initialize(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        Role admin = ensureRole(roles, "ADMINISTRADOR", "Acceso administrativo completo", true, Set.of(Permission.values()));
        Role analyst = ensureRole(roles, "ANALISTA", "Consulta y análisis operativo", true, Set.of(
                Permission.DASHBOARD_READ,
                Permission.INVENTORY_READ,
                Permission.INVENTORY_MOVEMENT_READ,
                Permission.QUOTATION_READ,
                Permission.SALE_READ
        ));
        Role seller = ensureRole(roles, "VENDEDOR", "Gestión comercial", true, Set.of(
                Permission.DASHBOARD_READ,
                Permission.CUSTOMER_READ,
                Permission.INVENTORY_READ,
                Permission.QUOTATION_READ,
                Permission.QUOTATION_CREATE,
                Permission.QUOTATION_UPDATE,
                Permission.QUOTATION_CONVERT,
                Permission.SALE_READ,
                Permission.SALE_CREATE,
                Permission.PAYMENT_CREATE
        ));

        createUserIfMissing(users, encoder, adminName, adminEmail, adminPassword, admin);
        createUserIfMissing(users, encoder, analystName, analystEmail, analystPassword, analyst);
        createUserIfMissing(users, encoder, sellerName, sellerEmail, sellerPassword, seller);
    }

    private Role ensureRole(
            RoleRepository roles,
            String name,
            String description,
            boolean system,
            Set<Permission> permissions
    ) {
        return roles.findByNameIgnoreCase(name).orElseGet(() -> {
            Role role = new Role();
            role.name = name;
            role.description = description;
            role.systemRole = system;
            role.permissions.addAll(permissions);
            return roles.save(role);
        });
    }

    private void createUserIfMissing(
            UserRepository users,
            PasswordEncoder encoder,
            String name,
            String email,
            String rawPassword,
            Role role
    ) {
        if (users.selectEmailCount(email, null) != 0) return;

        AppUser user = new AppUser();
        user.name = name;
        user.email = email;
        user.username = email.substring(0, email.indexOf('@'));
        user.password = encoder.encode(rawPassword);
        user.roles = new LinkedHashSet<>(Set.of(role));
        users.save(user);
    }
}
