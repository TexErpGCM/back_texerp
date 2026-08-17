package co.texerp.integrations.service;

import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.RefreshToken;
import co.texerp.integrations.exception.InvalidRefreshTokenException;
import co.texerp.integrations.repository.RefreshTokenRepository;
import co.texerp.integrations.security.RefreshTokenProperties;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_SIZE_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            RefreshTokenProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public IssuedRefreshToken create(AppUser user) {

        String rawToken = generateSecureToken();

        String tokenHash = hash(rawToken);

        Instant expiresAt = Instant.now().plus(
                properties.expirationDays(),
                ChronoUnit.DAYS
        );

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;

        repository.save(refreshToken);

        return new IssuedRefreshToken(
                rawToken,
                expiresAt
        );
    }

    public String hash(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(
                    "El refresh token no puede estar vacío"
            );
        }

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "No fue posible generar el hash del refresh token",
                    exception
            );
        }
    }

    private String generateSecureToken() {

        byte[] randomBytes =
                new byte[TOKEN_SIZE_BYTES];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    public record IssuedRefreshToken(
            String token,
            Instant expiresAt
    ) {
    }

    @Transactional
    public RotatedRefreshToken rotate(String rawToken) {

        String tokenHash = hash(rawToken);

        RefreshToken currentToken = repository
                .findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!currentToken.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        AppUser user = currentToken.user;

        if (!user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        currentToken.revokedAt = Instant.now();

        repository.save(currentToken);

        IssuedRefreshToken newRefreshToken =
                create(user);

        return new RotatedRefreshToken(
                user.getEmail(),
                newRefreshToken.token(),
                newRefreshToken.expiresAt()
        );
    }
    public record RotatedRefreshToken(
            String email,
            String token,
            Instant expiresAt
    ) {
    }
}