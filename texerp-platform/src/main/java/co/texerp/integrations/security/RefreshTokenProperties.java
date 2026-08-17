package co.texerp.integrations.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        long expirationDays
) {

    public long expirationDays() {
        return expirationDays <= 0
                ? 7
                : expirationDays;
    }
}