package iwo.wintech.ngnfincalc.platform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Component
public class RateLimitingFilter extends OncePerRequestFilter {
    private final RateLimitingProperty rateLimitingProperty;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createNewBucket() {
        // Limit: 20 requests per minute for auth endpoints
        final Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitingProperty.capacity())
                .refillIntervally(rateLimitingProperty.refillTokens(), rateLimitingProperty.refillInterval())
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain)
            throws ServletException, IOException {

        final String path = request.getRequestURI();

        // Apply rate limiting only to sensitive auth endpoints
        if (path.contains("/api/auth/login") || path.contains("/api/auth/register")) {
            final String ip = getClientIp(request);
            final Bucket bucket = buckets.computeIfAbsent(ip, k -> createNewBucket());

            if (bucket.tryConsume(1)) {
                filterChain.doFilter(request, response);
            } else {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"message\": \"Too many login/registration attempts. Please wait a minute.\", \"code\": \"RATE_LIMIT_EXCEEDED\"}");
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String getClientIp(final HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
