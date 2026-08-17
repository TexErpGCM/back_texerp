package co.texerp.integrations.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

@Entity
public class AppUser extends BaseEntity {

    @NotBlank
    public String name;

    @Email
    @Column(unique = true, nullable = false)
    public String email;

    @JsonIgnore
    @Column(nullable = false)
    public String password;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Role role;

    public boolean active = true;

    @Column(name = "last_login_at")
    public Instant lastLoginAt;

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}