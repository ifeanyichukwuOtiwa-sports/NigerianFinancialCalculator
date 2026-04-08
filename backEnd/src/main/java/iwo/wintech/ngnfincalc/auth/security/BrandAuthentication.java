package iwo.wintech.ngnfincalc.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public class BrandAuthentication extends AbstractAuthenticationToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String brand;
    private final String email;
    private final Long userId;
    private final String password;

    /**
     * Pre-auth constructor (login attempt — unauthenticated).
     */
    public BrandAuthentication(String brand, String email, String password) {
        super(List.of());
        this.brand = brand;
        this.email = email;
        this.password = password;
        this.userId = null;
        setAuthenticated(false);
    }

    /**
     * Post-auth constructor (authenticated — password cleared, userId resolved).
     */
    public BrandAuthentication(String brand, String email, Long userId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.brand = brand;
        this.email = email;
        this.userId = userId;
        this.password = null;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    public String getBrand() {
        return brand;
    }

    public String getEmail() {
        return email;
    }

    public Long getUserId() {
        return userId;
    }
}
