package iwo.wintech.ngnfincalc.config;

import iwo.wintech.ngnfincalc.exception.ErrorCode;
import iwo.wintech.ngnfincalc.exception.RequestException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TenantContext {

    public record TenantInfo(String brand, String email, Long userId) {}

    public TenantInfo getTenantInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof BrandAuthentication brandAuth) {
            return new TenantInfo(BrandContext.get(), brandAuth.getEmail(), brandAuth.getUserId());
        }
        throw new RequestException("No authenticated user found", ErrorCode.UNAUTHORIZED, Map.of());
    }
}
