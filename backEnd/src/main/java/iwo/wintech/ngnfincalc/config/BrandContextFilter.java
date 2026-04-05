package iwo.wintech.ngnfincalc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class BrandContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String brand = request.getHeader("X-App-Brand");
            if (brand != null && !brand.isBlank()) {
                BrandContext.set(brand.trim().toUpperCase());
            }
            filterChain.doFilter(request, response);
        } finally {
            BrandContext.clear();
        }
    }
}
