package co.texerp.integrations.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "refresh_token",
        indexes = {
                @Index(
                        name = "idx_refresh_token_hash",
                        columnList = "token_hash"
                ),
                @Index(
                        name = "idx_refresh_token_user",
                        columnList = "user_id"
                )
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    public AppUser user;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    public String tokenHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    public Instant expiresAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    public Instant createdAt;

    @Column(
            name = "revoked_at"
    )
    public Instant revokedAt;

    @PrePersist
    void prePersist() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }
}