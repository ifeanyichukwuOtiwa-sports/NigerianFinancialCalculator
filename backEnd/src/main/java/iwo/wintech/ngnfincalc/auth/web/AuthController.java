package iwo.wintech.ngnfincalc.auth.web;

import iwo.wintech.ngnfincalc.auth.dto.AuthResponse;
import iwo.wintech.ngnfincalc.auth.dto.LoginRequest;
import iwo.wintech.ngnfincalc.auth.dto.RegisterRequest;
import iwo.wintech.ngnfincalc.auth.security.BrandAuthentication;
import iwo.wintech.ngnfincalc.auth.service.AuthService;
import iwo.wintech.ngnfincalc.shared.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authService.login(request, httpServletRequest));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth instanceof BrandAuthentication brandAuth)) {
            // GlobalExceptionHandler renders the standard ApiErrorResponse body at 401.
            throw new UnauthorizedException("Authentication required");
        }
        return ResponseEntity.ok(authService.getCurrentUser(brandAuth));
    }
}
