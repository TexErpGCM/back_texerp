package co.texerp.integrations.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessExpirationMinutes
) {

    public long accessExpirationMinutes() {
        return accessExpirationMinutes <= 0
                ? 15
                : accessExpirationMinutes;
    }
}