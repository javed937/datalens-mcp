package com.datalens.mcp.exception;

public class DataLensException extends RuntimeException {

    public DataLensException(String message) {
        super(message);
    }

    public DataLensException(String message, Throwable cause) {
        super(message, cause);
    }
}
