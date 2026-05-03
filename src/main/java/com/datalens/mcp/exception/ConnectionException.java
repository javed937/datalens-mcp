package com.datalens.mcp.exception;

public class ConnectionException extends DataLensException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
