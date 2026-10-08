package co.texerp.integrations.controller;

import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Permission;
import co.texerp.integrations.domain.Role;
import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.dto.AuthDtos;
import co.texerp.integrations.repository.UserRepository;
import co.texerp.integrations.security.JwtProperties;
import co.texerp.integrations.security.JwtService;
import co.texerp.integrations.service.AuditService;
import co.texerp.integrations.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository users;
    private final AuditService audit;
    private final UserDetailsService userDetailsService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenService refreshTokenService,
            UserRepository users,
            AuditService audit,
            UserDetailsService userDetailsService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.users = users;
        this.audit = audit;
        this.userDetailsService = userDetailsService;
    }

    @PostMapping("/login")
    @Transactional
    public ApiResponse<AuthDtos.LoginResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest http
    ) {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        UserDetails principal =
                (UserDetails) authentication.getPrincipal();

        AppUser user =
                users.selectByEmail(request.email())
                        .orElseThrow();

        String accessToken =
                jwtService.generateAccessToken(principal);

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.create(user);

        Instant loginAt = Instant.now();

        users.updateLastLoginAt(
                user.email,
                loginAt
        );

        audit.log(
                "LOGIN",
                "AppUser",
                user.id,
                "Inicio de sesión exitoso",
                http
        );

        AuthDtos.UserSession session =
                toSession(user);

        return ApiResponse.ok(
                "Login exitoso",
                new AuthDtos.LoginResponse(
                        accessToken,
                        refreshToken.token(),
                        "Bearer",
                        jwtProperties.accessExpirationMinutes() * 60,
                        refreshToken.expiresAt(),
                        session
                )
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(
                "Logout lógico: elimina el token del frontend",
                null
        );
    }

    @PostMapping("/refresh")
    @Transactional
    public ApiResponse<AuthDtos.RefreshResponse> refresh(
            @Valid @RequestBody AuthDtos.RefreshRequest request
    ) {

        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotate(
                        request.refreshToken()
                );

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(
                        rotated.email()
                );

        String accessToken =
                jwtService.generateAccessToken(
                        userDetails
                );

        AppUser user =
                users.selectByEmail(
                        rotated.email()
                ).orElseThrow();

        AuthDtos.UserSession session =
                toSession(user);

        return ApiResponse.ok(
                "Sesión renovada correctamente",
                new AuthDtos.RefreshResponse(
                        accessToken,
                        rotated.token(),
                        "Bearer",
                        jwtProperties.accessExpirationMinutes() * 60,
                        rotated.expiresAt(),
                        session
                )
        );
    }

    private AuthDtos.UserSession toSession(
            AppUser user
    ) {

        Set<String> roles =
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet());

        Set<String> permissions =
                user.getRoles()
                        .stream()
                        .flatMap(
                                role -> role.getPermissions().stream()
                        )
                        .map(Permission::name)
                        .collect(Collectors.toSet());

        return new AuthDtos.UserSession(
                user.id,
                user.getName(),
                user.getUsername(),
                user.getEmail(),
                roles,
                permissions
        );
    }
}