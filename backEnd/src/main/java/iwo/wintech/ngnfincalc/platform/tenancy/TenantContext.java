package iwo.wintech.ngnfincalc.platform.tenancy;

import iwo.wintech.ngnfincalc.auth.security.BrandAuthentication;
import iwo.wintech.ngnfincalc.shared.error.ErrorCode;
import iwo.wintech.ngnfincalc.shared.error.RequestException;
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
            return new TenantInfo(brandAuth.getBrand(), brandAuth.getEmail(), brandAuth.getUserId());
        }
        throw new RequestException("No authenticated user found", ErrorCode.UNAUTHORIZED, Map.of());
    }
}
