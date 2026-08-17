package co.texerp.integrations.service;

import co.texerp.integrations.domain.AuditEvent;
import co.texerp.integrations.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    public void log(String action, String entity, Long id, String details, HttpServletRequest request) {
        log(action, entity, id, details, request, true);
    }

    public void log(
            String action,
            String entity,
            Long id,
            String details,
            HttpServletRequest request,
            boolean success
    ) {
        AuditEvent event = new AuditEvent();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        event.username = authentication == null || authentication.getName() == null
                ? "sistema"
                : truncate(authentication.getName(), 255);
        event.action = truncate(defaultText(action, "SIN_ACCION"), 255);
        event.entity = truncate(defaultText(entity, "SISTEMA"), 255);
        event.entityId = id;
        event.details = truncate(defaultText(details, "Sin detalle"), 1500);
        event.ip = truncate(resolveIp(request), 255);
        event.success = success;
        repo.save(event);
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
