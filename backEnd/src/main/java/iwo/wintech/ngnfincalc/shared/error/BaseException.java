package iwo.wintech.ngnfincalc.shared.error;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.event.Level;

@Getter
public abstract class BaseException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String uuid;
    private final Map<String, Object> params;
    private final Level logLevel;

    protected BaseException(String message, ErrorCode errorCode, Map<String, Object> params, Level logLevel) {
        super(message);
        this.errorCode = errorCode;
        this.uuid = UUID.randomUUID().toString();
        this.params = params;
        this.logLevel = logLevel;
    }

    protected BaseException(String message, ErrorCode errorCode, Map<String, Object> params, Level logLevel, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.uuid = UUID.randomUUID().toString();
        this.params = params;
        this.logLevel = logLevel;
    }
}
