package com.oj.rc.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Thrown by the services when a request must be answered with an error.
 * GlobalExceptionHandler turns it into the HTTP response.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Object body;

    /** Most errors: the response body is {"error": "<message>"}. */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.body = Map.of("error", message);
    }

    /** For the few endpoints whose error body has a different shape (see AdminService). */
    public ApiException(HttpStatus status, Object body) {
        super(String.valueOf(body));
        this.status = status;
        this.body = body;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object getBody() {
        return body;
    }
}
