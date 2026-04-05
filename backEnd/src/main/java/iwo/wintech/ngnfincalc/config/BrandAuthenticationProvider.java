package iwo.wintech.ngnfincalc.config;

import iwo.wintech.ngnfincalc.entity.User;
import iwo.wintech.ngnfincalc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        BrandAuthentication brandAuth = (BrandAuthentication) authentication;
        String brand = brandAuth.getBrand();
        String email = brandAuth.getEmail();
        String password = (String) brandAuth.getCredentials();

        User user = userRepository.findByBrandAndEmail(brand, email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return new BrandAuthentication(brand, email, user.id(), List.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BrandAuthentication.class.isAssignableFrom(authentication);
    }
}
