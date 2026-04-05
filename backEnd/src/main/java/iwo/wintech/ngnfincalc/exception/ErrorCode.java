package iwo.wintech.ngnfincalc.exception;

public enum ErrorCode {
    // Auth Errors
    EMAIL_EXISTS,
    USER_NOT_FOUND,
    USER_SYNC_ERROR,
    UNAUTHORIZED,
    AUTH_FAILED,

    // Request Errors
    INVALID_INPUT,
    VALIDATION_ERROR,

    // Server Errors
    INTERNAL_SERVER_ERROR,
    DATABASE_ERROR,
    UNEXPECTED_ERROR
}
