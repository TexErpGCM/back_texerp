package co.texerp.integrations.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter implements Filter {

    private record Bucket(AtomicInteger count, long window) {
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (isLimited(path)) {
            long window = Instant.now().getEpochSecond() / 60;
            int limit = path.startsWith("/api/v1/chat") ? 30 : 120;
            String group = path.startsWith("/api/v1/chat") ? "chat" : "public";
            String key = req.getRemoteAddr() + ':' + group;

            Bucket bucket = buckets.compute(key, (ignored, old) ->
                    old == null || old.window() != window
                            ? new Bucket(new AtomicInteger(), window)
                            : old);

            if (bucket.count().incrementAndGet() > limit) {
                res.setStatus(429);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"success\":false,\"message\":\"Límite de solicitudes excedido. Intenta nuevamente en un minuto.\"}");
                return;
            }

            if (buckets.size() > 10_000) {
                buckets.entrySet().removeIf(entry -> entry.getValue().window() < window - 2);
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isLimited(String path) {
        return path.startsWith("/api/v1/public")
                || path.startsWith("/api/v1/chat")
                || path.startsWith("/api/v1/resultados/live");
    }
}
