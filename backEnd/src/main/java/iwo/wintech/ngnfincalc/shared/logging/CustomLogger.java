package iwo.wintech.ngnfincalc.shared.logging;

import iwo.wintech.ngnfincalc.shared.error.BaseException;
import iwo.wintech.ngnfincalc.shared.error.CustomStatusResolver;
import iwo.wintech.ngnfincalc.shared.error.RequestException;
import iwo.wintech.ngnfincalc.shared.error.ServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomLogger {

    private final CustomStatusResolver statusResolver;

    public void logException(Throwable throwable) {
        Level level = resolveLogLevel(throwable);
        
        if (throwable instanceof BaseException ex) {
            logWithLevel(level, buildMessage(ex), ex instanceof ServerException ? ex : null);
        } else {
            logWithLevel(level, "[UNEXPECTED_ERROR] Message: " + throwable.getMessage(), throwable);
        }
    }

    private Level resolveLogLevel(Throwable throwable) {
        Level defaultLevel = throwable instanceof BaseException be ? be.getLogLevel() : Level.ERROR;
        return statusResolver.resolveLogLevel(throwable, defaultLevel);
    }

    private String buildMessage(BaseException ex) {
        String type = ex instanceof RequestException ? "REQUEST_ERROR" : "SERVER_ERROR";
        return String.format("[%s] Code: %s, UUID: %s, Message: %s, Params: %s",
            type, ex.getErrorCode().name(), ex.getUuid(), ex.getMessage(), ex.getParams());
    }

    private void logWithLevel(Level level, String message, Throwable throwable) {
        switch (level) {
            case ERROR -> log.error(message, throwable);
            case WARN -> log.warn(message, throwable);
            case INFO -> log.info(message, throwable);
            case DEBUG -> log.debug(message, throwable);
            case TRACE -> log.trace(message, throwable);
        }
    }
}
