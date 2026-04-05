package iwo.wintech.ngnfincalc.exception;

import org.slf4j.event.Level;
import java.util.Map;

public class ServerException extends BaseException {
    public ServerException(String message, ErrorCode errorCode) {
        this(message, errorCode, Map.of());
    }

    public ServerException(String message, ErrorCode errorCode, Map<String, Object> params) {
        this(message, errorCode, params, Level.ERROR);
    }

    public ServerException(String message, ErrorCode errorCode, Map<String, Object> params, Level logLevel) {
        super(message, errorCode, params, logLevel);
    }

    public ServerException(String message, ErrorCode errorCode, Map<String, Object> params, Throwable cause) {
        this(message, errorCode, params, Level.ERROR, cause);
    }

    public ServerException(String message, ErrorCode errorCode, Map<String, Object> params, Level logLevel, Throwable cause) {
        super(message, errorCode, params, logLevel, cause);
    }
}
