package iwo.wintech.ngnfincalc.exception;

import org.slf4j.event.Level;
import java.util.Map;

public class RequestException extends BaseException {
    public RequestException(String message, ErrorCode errorCode) {
        this(message, errorCode, Map.of());
    }

    public RequestException(String message, ErrorCode errorCode, Map<String, Object> params) {
        this(message, errorCode, params, Level.INFO);
    }

    public RequestException(String message, ErrorCode errorCode, Map<String, Object> params, Level logLevel) {
        super(message, errorCode, params, logLevel);
    }
}
