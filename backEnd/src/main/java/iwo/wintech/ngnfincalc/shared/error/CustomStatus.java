package iwo.wintech.ngnfincalc.shared.error;

import org.slf4j.event.Level;

public record CustomStatus(
    Class<? extends Throwable> exceptionClass,
    int statusCode,
    Level logLevel
) {
    public static CustomStatus of(Class<? extends Throwable> exceptionClass, int statusCode, Level logLevel) {
        return new CustomStatus(exceptionClass, statusCode, logLevel);
    }
}
