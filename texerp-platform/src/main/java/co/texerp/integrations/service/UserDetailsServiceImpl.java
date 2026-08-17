package co.texerp.integrations.service;

import co.texerp.integrations.domain.Role;
import co.texerp.integrations.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository repository;

    public UserDetailsServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {

        var user = repository.selectByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Usuario no encontrado"
                        )
                );

        Role role = user.getRole();

        if (role == null) {
            throw new UsernameNotFoundException(
                    "Usuario no disponible"
            );
        }

        List<SimpleGrantedAuthority> authorities =
                new ArrayList<>();

        // Rol
        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_" + role.name()
                )
        );

        // Permisos asociados al rol
        role.getPermissions()
                .forEach(permission ->
                        authorities.add(
                                new SimpleGrantedAuthority(
                                        permission.name()
                                )
                        )
                );

        return new User(
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                true,
                true,
                true,
                authorities
        );
    }
}