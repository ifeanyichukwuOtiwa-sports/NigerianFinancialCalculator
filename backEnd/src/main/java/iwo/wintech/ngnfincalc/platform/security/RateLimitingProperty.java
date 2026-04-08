package iwo.wintech.ngnfincalc.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties( prefix = "rate.limiting")
public record RateLimitingProperty(
        int capacity,
        int refillTokens,
        Duration refillInterval
) {
}
