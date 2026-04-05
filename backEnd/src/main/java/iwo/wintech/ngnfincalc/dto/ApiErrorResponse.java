package iwo.wintech.ngnfincalc.dto;

import iwo.wintech.ngnfincalc.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;

public record ApiErrorResponse(
    ErrorCode code,
    String uuid,
    String message,
    Map<String, Object> params
) {
    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, Object> params) {
        return new ApiErrorResponse(code, UUID.randomUUID().toString(), message, params);
    }
}
