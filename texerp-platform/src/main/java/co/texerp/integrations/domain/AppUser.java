package co.texerp.integrations.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_app_user_username",
                        columnNames = "username"
                ),
                @UniqueConstraint(
                        name = "uk_app_user_email",
                        columnNames = "email"
                )
        }
)
public class AppUser extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    public String name;

    @Column(
            name = "username",
            nullable = false
    )
    public String username;

    @Email
    @NotBlank
    @Column(nullable = false)
    public String email;

    @JsonIgnore
    @Column(nullable = false)
    public String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "app_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_app_user_role",
                    columnNames = {
                            "user_id",
                            "role_id"
                    }
            )
    )
    public Set<Role> roles =
            new LinkedHashSet<>();

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Set<Permission> getEffectivePermissions() {

        Set<Permission> effective =
                new LinkedHashSet<>();

        roles.forEach(
                role ->
                        effective.addAll(
                                role.getPermissions()
                        )
        );

        return effective;
    }
}