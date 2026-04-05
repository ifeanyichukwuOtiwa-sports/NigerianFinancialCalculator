package iwo.wintech.ngnfincalc.service;

import iwo.wintech.ngnfincalc.config.BrandAuthentication;
import iwo.wintech.ngnfincalc.config.BrandContext;
import iwo.wintech.ngnfincalc.exception.ErrorCode;
import iwo.wintech.ngnfincalc.exception.RequestException;
import iwo.wintech.ngnfincalc.entity.User;
import iwo.wintech.ngnfincalc.dto.AuthResponse;
import iwo.wintech.ngnfincalc.dto.LoginRequest;
import iwo.wintech.ngnfincalc.dto.RegisterRequest;
import iwo.wintech.ngnfincalc.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        String brand = BrandContext.get();

        if (userRepository.findByBrandAndEmail(brand, request.email()).isPresent()) {
            throw new RequestException("Email already exists for this brand", ErrorCode.EMAIL_EXISTS,
                Map.of("email", request.email(), "brand", brand));
        }

        User user = User.builder()
                .brand(brand)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .build();

        User savedUser = userRepository.save(user);
        return mapToResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        String brand = BrandContext.get();

        Authentication authentication = authenticationManager.authenticate(
                new BrandAuthentication(brand, request.email(), request.password())
        );

        SecurityContext sc = SecurityContextHolder.getContext();
        sc.setAuthentication(authentication);

        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sc);

        BrandAuthentication brandAuth = (BrandAuthentication) authentication;
        return getCurrentUser(brandAuth);
    }

    public AuthResponse getCurrentUser(BrandAuthentication brandAuth) {
        return userRepository.findById(brandAuth.getUserId())
                .map(this::mapToResponse)
                .orElseThrow(() -> new RequestException("User not found", ErrorCode.INVALID_INPUT, Map.of()));
    }

    private AuthResponse mapToResponse(User user) {
        return new AuthResponse(
            user.id(),
            user.email(),
            user.fullName()
        );
    }
}
