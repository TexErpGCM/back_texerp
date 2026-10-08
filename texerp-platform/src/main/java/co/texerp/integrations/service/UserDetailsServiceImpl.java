package co.texerp.integrations.service;

import co.texerp.integrations.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository repository;

    public UserDetailsServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var user = repository.selectByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (user.roles == null || user.roles.isEmpty()) {
            throw new UsernameNotFoundException("Usuario sin roles asignados");
        }

        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();

        user.roles.forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name));
            role.permissions.forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority(permission.name()))
            );
        });

        return new User(
                user.email,
                user.password,
                user.active,
                true,
                true,
                true,
                authorities
        );
    }
}
