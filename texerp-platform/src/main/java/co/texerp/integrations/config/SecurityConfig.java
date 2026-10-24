
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
                .csrf(csrf -> csrf.disable())

                .headers(headers ->
                        headers.frameOptions(frame -> frame.sameOrigin())
                )

                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // AUTH - PUBLICO
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()

                        // ENDPOINTS PUBLICOS
                        .requestMatchers("/api/v1/public/**")
                        .permitAll()

                        // H2 CONSOLE
                        .requestMatchers("/h2-console/**")
                        .permitAll()

                        // SWAGGER
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        // ACTUATOR
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        // USUARIOS
                        .requestMatchers(HttpMethod.POST, "/api/v1/users")
                        .hasAuthority("USER_CREATE")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/users",
                                "/api/v1/users/**"
                        )
                        .hasAuthority("USER_READ")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/**")
                        .hasAuthority("USER_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/**")
                        .hasAuthority("USER_UPDATE")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/**")
                        .hasAuthority("USER_DELETE")

                        // ROLES Y PERMISOS
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles/permissions")
                        .hasAuthority("PERMISSION_READ")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/roles",
                                "/api/v1/roles/**"
                        )
                        .hasAuthority("ROLE_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/roles")
                        .hasAuthority("ROLE_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/**")
                        .hasAuthority("ROLE_UPDATE")

                        .requestMatchers(HttpMethod.DELETE, "/api/v1/roles/**")
                        .hasAuthority("ROLE_DELETE")

                        // CLIENTES
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/customers",
                                "/api/v1/customers/**"
                        )
                        .hasAuthority("CUSTOMER_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/customers")
                        .hasAuthority("CUSTOMER_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/customers/**")
                        .hasAuthority("CUSTOMER_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/customers/**")
                        .hasAuthority("CUSTOMER_UPDATE")

                        // BODEGAS
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/warehouses",
                                "/api/v1/warehouses/**"
                        )
                        .hasAuthority("WAREHOUSE_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/warehouses")
                        .hasAuthority("WAREHOUSE_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/warehouses/**")
                        .hasAuthority("WAREHOUSE_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/warehouses/**")
                        .hasAuthority("WAREHOUSE_UPDATE")

                        // PRODUCTOS
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        )
                        .hasAuthority("PRODUCT_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/products")
                        .hasAuthority("PRODUCT_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/products/**")
                        .hasAuthority("PRODUCT_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**")
                        .hasAuthority("PRODUCT_UPDATE")

                        // VARIANTES
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/variants",
                                "/api/v1/variants/**"
                        )
                        .hasAuthority("PRODUCT_VARIANT_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/products/*/variants"
                        )
                        .hasAuthority("PRODUCT_VARIANT_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/variants/**")
                        .hasAuthority("PRODUCT_VARIANT_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/variants/**")
                        .hasAuthority("PRODUCT_VARIANT_UPDATE")

                        // PROVEEDORES
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/suppliers",
                                "/api/v1/suppliers/**"
                        )
                        .hasAuthority("SUPPLIER_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/suppliers")
                        .hasAuthority("SUPPLIER_CREATE")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/suppliers/**")
                        .hasAuthority("SUPPLIER_UPDATE")

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/suppliers/**")
                        .hasAuthority("SUPPLIER_UPDATE")

                        // AUDITORIA
                        .requestMatchers(
                                "/api/v1/audit/**",
                                "/api/v1/auditoria/**"
                        )
                        .hasAuthority("AUDIT_READ")

                        // INVENTARIO
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/inventory",
                                "/api/v1/inventory/low-stock",
                                "/api/v1/inventory/sku/**"
                        )
                        .hasAuthority("INVENTORY_READ")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/inventory/movements",
                                "/api/v1/inventory/movements/**"
                        )
                        .hasAuthority("INVENTORY_MOVEMENT_READ")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/inventory/variants/*/warehouses/*/minimum"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/inventory/adjustments"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/inventory/movements/*/compensations"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/inventory/movements/**"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/inventory/movements/**"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/v1/inventory/movements/**"
                        )
                        .hasAuthority("INVENTORY_ADJUST")

                        // COTIZACIONES - DOS RUTAS COMPATIBLES
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/quotations",
                                "/api/v1/quotations/**",
                                "/api/v1/quotes",
                                "/api/v1/quotes/**"
                        )
                        .hasAuthority("QUOTATION_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/quotations",
                                "/api/v1/quotes"
                        )
                        .hasAuthority("QUOTATION_CREATE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/quotations/*/send",
                                "/api/v1/quotations/*/cancel",
                                "/api/v1/quotes/*/send",
                                "/api/v1/quotes/*/cancel"
                        )
                        .hasAuthority("QUOTATION_UPDATE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/quotations/*/convert",
                                "/api/v1/quotes/*/convert"
                        )
                        .hasAuthority("QUOTATION_CONVERT")

                        // VENTAS Y PAGOS
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/sales/**"
                        )
                        .hasAuthority("SALE_READ")

                        .requestMatchers(HttpMethod.POST, "/api/v1/sales")
                        .hasAuthority("SALE_CREATE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/sales/*/payments"
                        )
                        .hasAuthority("PAYMENT_CREATE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/sales/*/dispatch"
                        )
                        .hasAuthority("SALE_DISPATCH")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/sales/*/deliver"
                        )
                        .hasAuthority("SALE_DELIVER")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/sales/*/cancel"
                        )
                        .hasAuthority("SALE_CANCEL")

                        // ORDENES DE COMPRA
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/purchase-orders/**"
                        )
                        .hasAuthority("PURCHASE_ORDER_READ")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/purchase-orders"
                        )
                        .hasAuthority("PURCHASE_ORDER_CREATE")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/purchase-orders/*/approve",
                                "/api/v1/purchase-orders/*/reject"
                        )
                        .hasAuthority("PURCHASE_ORDER_APPROVE")

                        // RESTO DEL SISTEMA
                        .anyRequest()
                        .authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    JwtService jwtService(JwtProperties properties) {
        return new JwtService(properties);
    }

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

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
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

        config.setAllowedHeaders(List.of("*"));

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
