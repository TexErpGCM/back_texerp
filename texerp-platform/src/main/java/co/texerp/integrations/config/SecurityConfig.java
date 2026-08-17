package co.texerp.integrations.config;

import co.texerp.integrations.security.JwtAuthenticationFilter;
import co.texerp.integrations.security.JwtProperties;
import co.texerp.integrations.security.JwtService;
import co.texerp.integrations.security.RefreshTokenProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        RefreshTokenProperties.class
})
public class SecurityConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200}")
    private String allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http

                // =========================
                // CSRF
                // =========================
                .csrf(csrf -> csrf.disable())

                // =========================
                // HEADERS
                // =========================
                // Necesario para H2 Console
                .headers(headers ->
                        headers.frameOptions(frame -> frame.sameOrigin())
                )

                // =========================
                // CORS
                // =========================
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                // =========================
                // SESIONES
                // =========================
                // API stateless con JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // =========================
                // AUTORIZACIONES
                // =========================
                .authorizeHttpRequests(auth -> auth

                        // =========================
                        // AUTH - PÚBLICO
                        // =========================
                        .requestMatchers(
                                "/api/v1/auth/**"
                        )
                        .permitAll()

                        // =========================
                        // ENDPOINTS PÚBLICOS
                        // =========================
                        .requestMatchers(
                                "/api/v1/public/**"
                        )
                        .permitAll()

                        // =========================
                        // H2 CONSOLE
                        // SOLO DESARROLLO
                        // =========================
                        .requestMatchers(
                                "/h2-console/**"
                        )
                        .permitAll()

                        // =========================
                        // SWAGGER
                        // =========================
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        // =========================
                        // ACTUATOR
                        // =========================
                        .requestMatchers(
                                "/actuator/health"
                        )
                        .permitAll()

                        // =========================
                        // USUARIOS
                        // =========================

                        // Crear usuario
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/users"
                        )
                        .hasAuthority("USER_CREATE")

                        // Consultar usuarios
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users",
                                "/api/v1/users/**"
                        )
                        .hasAuthority("USER_READ")

                        // Actualizar usuario
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/users/**"
                        )
                        .hasAuthority("USER_UPDATE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/users/**"
                        )
                        .hasAuthority("USER_UPDATE")

                        // Eliminar usuario
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/users/**"
                        )
                        .hasAuthority("USER_DELETE")

                        // =========================
                        // AUDITORÍA
                        // =========================
                        .requestMatchers(
                                "/api/v1/audit/**",
                                "/api/v1/auditoria/**"
                        )
                        .hasAuthority("AUDIT_READ")

                        // =========================
                        // RESTO DEL SISTEMA
                        // =========================
                        .anyRequest()
                        .authenticated()
                )

                // =========================
                // JWT FILTER
                // =========================
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    // =========================
    // JWT SERVICE
    // =========================
    @Bean
    JwtService jwtService(
            JwtProperties properties
    ) {
        return new JwtService(properties);
    }

    // =========================
    // JWT FILTER
    // =========================
    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService
    ) {
        return new JwtAuthenticationFilter(
                jwtService,
                userDetailsService
        );
    }

    // =========================
    // PASSWORD ENCODER
    // =========================
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================
    // AUTHENTICATION MANAGER
    // =========================
    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // =========================
    // CORS
    // =========================
    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config =
                new CorsConfiguration();

        config.setAllowedOrigins(
                Arrays.stream(
                                allowedOrigins.split(",")
                        )
                        .map(String::trim)
                        .filter(
                                origin ->
                                        !origin.isBlank()
                        )
                        .toList()
        );

        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        config.setAllowedHeaders(
                List.of("*")
        );

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                config
        );

        return source;
    }
}