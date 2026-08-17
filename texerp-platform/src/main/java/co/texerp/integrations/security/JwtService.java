package co.texerp.integrations.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtService {

  private final JwtProperties properties;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
  }

  private SecretKey key() {

    String secret = properties.secret();

    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
              "TEXERP_JWT_SECRET debe contener al menos 32 caracteres"
      );
    }

    return Keys.hmacShaKeyFor(
            secret.getBytes(StandardCharsets.UTF_8)
    );
  }

  public String generateAccessToken(UserDetails user) {

    Map<String, Object> claims = new HashMap<>();

    claims.put(
            "authorities",
            user.getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList()
    );

    claims.put("type", "ACCESS");

    Instant now = Instant.now();

    Instant expiration = now.plusSeconds(
            properties.accessExpirationMinutes() * 60
    );

    return Jwts.builder()
            .claims(claims)
            .subject(user.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(key())
            .compact();
  }

  public String username(String token) {
    return claims(token).getSubject();
  }

  public boolean validAccessToken(
          String token,
          UserDetails user
  ) {
    try {

      Claims claims = claims(token);

      String username = claims.getSubject();

      String type = claims.get(
              "type",
              String.class
      );

      Date expiration = claims.getExpiration();

      return username.equals(user.getUsername())
              && "ACCESS".equals(type)
              && expiration.after(new Date());

    } catch (Exception exception) {
      return false;
    }
  }

  private Claims claims(String token) {

    return Jwts.parser()
            .verifyWith(key())
            .build()
            .parseSignedClaims(token)
            .getPayload();
  }
}