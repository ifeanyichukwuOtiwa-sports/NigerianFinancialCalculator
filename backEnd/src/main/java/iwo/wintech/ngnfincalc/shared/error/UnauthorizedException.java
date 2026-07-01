package iwo.wintech.ngnfincalc.shared.error;

import org.slf4j.event.Level;

import java.util.Map;

/**
 * Request reached a protected resource without a valid authenticated principal. Maps to HTTP 401.
 */
public class UnauthorizedException extends BaseException {
    public UnauthorizedException(String message) {
        super(message, ErrorCode.UNAUTHORIZED, Map.of(), Level.INFO);
    }
}
