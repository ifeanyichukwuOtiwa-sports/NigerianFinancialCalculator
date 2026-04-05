package iwo.wintech.ngnfincalc.config;

import iwo.wintech.ngnfincalc.dto.ApiErrorResponse;
import iwo.wintech.ngnfincalc.exception.BaseException;
import iwo.wintech.ngnfincalc.exception.ErrorCode;
import iwo.wintech.ngnfincalc.exception.RequestException;
import iwo.wintech.ngnfincalc.logging.CustomLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final CustomLogger customLogger;
    private final CustomStatusResolver statusResolver;

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiErrorResponse> handleBaseException(final BaseException ex) {
        customLogger.logException(ex);
        
        int status = statusResolver.resolveStatusCode(ex, ex instanceof RequestException ? 400 : 500);

        return ResponseEntity.status(status).body(new ApiErrorResponse(
                ex.getErrorCode(),
                ex.getUuid(),
                ex.getMessage(),
                ex.getParams()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(final MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        int status = statusResolver.resolveStatusCode(ex, 400);

        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, "Input validation failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(final BadCredentialsException ex) {
        int status = statusResolver.resolveStatusCode(ex, 401);
        
        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(ErrorCode.AUTH_FAILED, "Invalid email or password", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(final Exception ex) {
        customLogger.logException(ex);
        
        int status = statusResolver.resolveStatusCode(ex, 500);

        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, "A system error occurred",
                        Map.of("details", ex.getMessage() != null ? ex.getMessage() : "No message")));
    }
}
