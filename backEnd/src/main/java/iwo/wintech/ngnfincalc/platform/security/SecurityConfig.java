package iwo.wintech.ngnfincalc.platform.security;

import iwo.wintech.ngnfincalc.auth.security.BrandAuthenticationProvider;
import iwo.wintech.ngnfincalc.platform.tenancy.BrandContextFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@ConfigurationPropertiesScan
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                   final BrandContextFilter brandContextFilter,
                                                   final RateLimitingFilter rateLimitingFilter,
                                                   final CorsConfig config) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource(config)))
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                // Clickjacking: JSON API + separate SPA, deny all framing.
                .frameOptions(frame -> frame.deny())
                // MIME sniffing protection.
                .contentTypeOptions(withDefaults())
                // HSTS — prod is HTTPS-only (Secure cookies already required).
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000))
                // Don't leak full URLs cross-origin.
                .referrerPolicy(ref -> ref.policy(
                    ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // Backend serves only JSON; lock CSP right down (governs API/error responses, not the SPA).
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
            )
            .addFilterBefore(brandContextFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, BrandContextFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(sc -> sc
                .securityContextRepository(new HttpSessionSecurityContextRepository())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(
                        (_, response, _) -> response.setStatus(200)
                )
                .deleteCookies("SESSION")
                .invalidateHttpSession(true)
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(final CorsConfig config) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(config.allowedOrigins());
        configuration.setAllowedMethods(config.allowedMethods());
        configuration.setAllowedHeaders(config.allowedHeaders());
        configuration.setExposedHeaders(config.exposedHeaders());
        configuration.setAllowCredentials(config.allowCredentials());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(final BrandAuthenticationProvider brandAuthenticationProvider) {
        return new ProviderManager(brandAuthenticationProvider);
    }
}
