package com.ncba.countryinfo.exception;

/** Upstream (SOAP) failure: timeout, 5xx, network error or malformed response. Retried + counted by the circuit breaker. */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) { super(message); }
    public ExternalServiceException(String message, Throwable cause) { super(message, cause); }
}
